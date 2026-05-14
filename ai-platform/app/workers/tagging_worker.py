import logging
from typing import Optional

from app.workers.base_worker import BaseWorker
from app.services.tagging_service import tagging_service

logger = logging.getLogger(__name__)


class TaggingWorker(BaseWorker):
    @property
    def queue_name(self) -> str:
        return "ai.tagging.submit"

    @property
    def result_queue(self) -> Optional[str]:
        return "ai.tagging.result"

    async def process(self, data: dict) -> Optional[dict]:
        image_url = data.get("image_url", "")
        top_k = data.get("top_k", 5)
        custom_labels = data.get("custom_labels")
        tags, cached = await tagging_service.tag_image(
            image_url=image_url, top_k=top_k, custom_labels=custom_labels,
        )
        return {
            "image_url": image_url,
            "tags": tags,
            "cached": cached,
        }
