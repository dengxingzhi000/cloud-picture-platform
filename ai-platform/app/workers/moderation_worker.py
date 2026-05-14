import logging
from typing import Optional

from app.workers.base_worker import BaseWorker
from app.services.moderation_service import moderation_service

logger = logging.getLogger(__name__)


class ModerationWorker(BaseWorker):
    @property
    def queue_name(self) -> str:
        return "ai.moderation.submit"

    @property
    def result_queue(self) -> Optional[str]:
        return "ai.moderation.result"

    async def process(self, data: dict) -> Optional[dict]:
        content = data.get("content", "")
        content_type = data.get("type", "text")
        provider = data.get("provider")

        if content_type == "image":
            result, cached = await moderation_service.moderate_image(
                image_url=content, provider=provider,
            )
        else:
            result, cached = await moderation_service.moderate_text(
                text=content, provider=provider,
            )
        return {
            "content": content,
            "type": content_type,
            "result": result,
            "cached": cached,
        }
