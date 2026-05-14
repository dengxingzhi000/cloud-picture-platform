import time
import logging
from typing import Optional

from app.models.base import BaseAiModel, ModelInfo

logger = logging.getLogger(__name__)


class QwenVlModel(BaseAiModel):
    def __init__(self, model_path: str = ""):
        self._model_path = model_path
        self._model = None
        self._processor = None
        self._loaded = False

    def load(self):
        if not self._loaded and self._model_path:
            logger.info("Qwen-VL model loading is a stub")
            self._loaded = True

    @property
    def model_info(self) -> ModelInfo:
        return ModelInfo(
            name="qwen-vl",
            version="1.0",
            provider="local" if self._model_path else "none",
            loaded_at=time.time() if self._loaded else 0,
            device="cpu",
        )

    def is_ready(self) -> bool:
        return self._loaded

    async def describe_image(self, image_url: str, prompt: Optional[str] = None) -> str:
        self.load()
        logger.info("describe_image stub called for %s", image_url)
        return "Qwen-VL image description stub"

    async def extract_keywords(self, image_url: str) -> list[str]:
        self.load()
        logger.info("extract_keywords stub called for %s", image_url)
        return ["stub", "keyword"]
