import time
import logging
import requests
from io import BytesIO
from PIL import Image

from app.models.base import BaseAiModel, ModelInfo

logger = logging.getLogger(__name__)


class YoloModel(BaseAiModel):
    def __init__(self, model_name: str = "yolov8n.pt", confidence_threshold: float = 0.3):
        self._model_name = model_name
        self._confidence_threshold = confidence_threshold
        self._model = None
        self._loaded_at = None

    def load(self):
        if self._model is not None:
            return
        try:
            from ultralytics import YOLO
            logger.info("Loading YOLO model: %s", self._model_name)
            self._model = YOLO(self._model_name)
            self._loaded_at = time.time()
            logger.info("YOLO model loaded")
        except Exception:
            logger.exception("Failed to load YOLO model")

    @property
    def model_info(self) -> ModelInfo:
        return ModelInfo(
            name="yolo",
            version=self._model_name,
            provider="local",
            loaded_at=self._loaded_at or 0,
            device="cpu",
        )

    def is_ready(self) -> bool:
        return self._model is not None

    def detect(self, image_url: str) -> list[dict]:
        self.load()
        if self._model is None:
            return []
        try:
            resp = requests.get(image_url, timeout=10)
            resp.raise_for_status()
            image = Image.open(BytesIO(resp.content)).convert("RGB")
            results = self._model(image, verbose=False)
            detections = []
            seen_labels = set()
            for result in results:
                for box in result.boxes:
                    conf = float(box.conf[0])
                    if conf < self._confidence_threshold:
                        continue
                    label = result.names[int(box.cls[0])]
                    if label in seen_labels:
                        continue
                    seen_labels.add(label)
                    detections.append({
                        "label": label,
                        "confidence": conf,
                        "bbox": box.xyxy[0].tolist(),
                    })
            detections.sort(key=lambda x: x["confidence"], reverse=True)
            return detections
        except Exception:
            logger.exception("YOLO detection failed for %s", image_url)
            return []

    def get_object_names(self, image_url: str) -> list[str]:
        detections = self.detect(image_url)
        return [d["label"] for d in detections]
