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
        image_url = data.get("image_url") or data.get("imageUrl", "")
        picture_id = data.get("pictureId", "")
        top_k = data.get("top_k", 5)
        custom_labels = data.get("custom_labels")

        tags, description, detections, cached = await tagging_service.tag_image_combined(
            image_url=image_url, top_k=top_k, custom_labels=custom_labels,
        )

        return {
            "pictureId": picture_id,
            "success": True,
            "tags": tags,
            "description": description,
            "detections": detections,
            "provider": "combined",
            "cached": cached,
        }
