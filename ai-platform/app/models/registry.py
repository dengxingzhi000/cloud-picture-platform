import logging
from app.models.clip_model import ClipModel
from app.models.moderation_model import ModerationModel
from app.models.florence_model import FlorenceModel
from app.models.yolo_model import YoloModel

logger = logging.getLogger(__name__)


class ModelRegistry:
    def __init__(self, config):
        self._models = {}
        self._config = config

    def get_clip(self) -> ClipModel:
        if "clip" not in self._models:
            model = ClipModel(
                model_name=self._config.clip_model_name,
                pretrained=self._config.clip_pretrained,
            )
            model.load()
            self._models["clip"] = model
            logger.info("CLIP model loaded")
        return self._models["clip"]

    def get_moderation(self) -> ModerationModel:
        if "moderation" not in self._models:
            model = ModerationModel(
                provider=self._config.moderation_provider,
                aliyun_ak=self._config.aliyun_ak,
                aliyun_sk=self._config.aliyun_sk,
            )
            self._models["moderation"] = model
            logger.info("Moderation model initialized")
        return self._models["moderation"]

    def get_florence(self) -> FlorenceModel:
        if "florence" not in self._models:
            model = FlorenceModel(
                model_name=self._config.florence_model_name,
            )
            if self._config.florence_enabled:
                model.load()
            self._models["florence"] = model
            logger.info("Florence model initialized")
        return self._models["florence"]

    def get_yolo(self) -> YoloModel:
        if "yolo" not in self._models:
            model = YoloModel(
                model_name=self._config.yolo_model_name,
                confidence_threshold=self._config.yolo_confidence_threshold,
            )
            if self._config.yolo_enabled:
                model.load()
            self._models["yolo"] = model
            logger.info("YOLO model initialized")
        return self._models["yolo"]

    def health_report(self) -> dict:
        report = {}
        for name, model in self._models.items():
            report[name] = model.health_check()
        for name in ("clip", "moderation", "florence", "yolo"):
            if name not in self._models:
                report[name] = {"ready": False, "loaded": False}
        return report


_registry: ModelRegistry | None = None


def get_registry() -> ModelRegistry:
    if _registry is None:
        raise RuntimeError("ModelRegistry not initialized. Call init_registry() first.")
    return _registry


def init_registry(config) -> ModelRegistry:
    global _registry
    _registry = ModelRegistry(config)
    return _registry
