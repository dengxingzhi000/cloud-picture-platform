import time
import logging
import requests
from io import BytesIO
from PIL import Image
import torch

from app.models.base import BaseAiModel, ModelInfo

logger = logging.getLogger(__name__)


class FlorenceModel(BaseAiModel):
    def __init__(self, model_name: str = "microsoft/Florence-2-base"):
        self._model_name = model_name
        self._model = None
        self._processor = None
        self._device = "cuda" if torch.cuda.is_available() else "cpu"
        self._loaded_at = None

    def load(self):
        if self._model is not None:
            return
        try:
            from transformers import AutoModelForCausalLM, AutoProcessor
            logger.info("Loading Florence-2 model: %s", self._model_name)
            self._processor = AutoProcessor.from_pretrained(
                self._model_name, trust_remote_code=True
            )
            self._model = AutoModelForCausalLM.from_pretrained(
                self._model_name,
                torch_dtype=torch.float16 if self._device == "cuda" else torch.float32,
                trust_remote_code=True,
            ).to(self._device)
            self._model.eval()
            self._loaded_at = time.time()
            logger.info("Florence-2 model loaded on %s", self._device)
        except Exception:
            logger.exception("Failed to load Florence-2 model")

    @property
    def model_info(self) -> ModelInfo:
        return ModelInfo(
            name="florence-2",
            version=self._model_name,
            provider="local",
            loaded_at=self._loaded_at or 0,
            device=self._device,
        )

    def is_ready(self) -> bool:
        return self._model is not None

    def _download_image(self, image_url: str) -> Image.Image:
        resp = requests.get(image_url, timeout=10)
        resp.raise_for_status()
        return Image.open(BytesIO(resp.content)).convert("RGB")

    def describe_image(self, image_url: str) -> str:
        self.load()
        if self._model is None:
            return ""
        image = self._download_image(image_url)
        task = "<MORE_DETAILED_CAPTION>"
        inputs = self._processor(text=task, images=image, return_tensors="pt").to(self._device)
        with torch.no_grad():
            generated_ids = self._model.generate(
                **inputs, max_new_tokens=256, num_beams=3
            )
        generated_text = self._processor.batch_decode(
            generated_ids, skip_special_tokens=False
        )[0]
        return self._processor.post_process_generation(
            generated_text, task=task, image_size=(image.width, image.height)
        ).get(task, generated_text)

    def caption_image(self, image_url: str) -> str:
        self.load()
        if self._model is None:
            return ""
        image = self._download_image(image_url)
        task = "<CAPTION>"
        inputs = self._processor(text=task, images=image, return_tensors="pt").to(self._device)
        with torch.no_grad():
            generated_ids = self._model.generate(
                **inputs, max_new_tokens=64, num_beams=3
            )
        generated_text = self._processor.batch_decode(
            generated_ids, skip_special_tokens=False
        )[0]
        return self._processor.post_process_generation(
            generated_text, task=task, image_size=(image.width, image.height)
        ).get(task, generated_text)
