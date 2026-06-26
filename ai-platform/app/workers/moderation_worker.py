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
        picture_id = data.get("pictureId", "")
        provider = data.get("provider")

        if content_type == "image":
            result, cached = await moderation_service.moderate_image(
                image_url=content, provider=provider,
            )
        else:
            result, cached = await moderation_service.moderate_text(
                text=content, provider=provider,
            )

        violations = result.get("violations", [])
        if not violations and result.get("labels"):
            violations = [l if isinstance(l, str) else l.get("name", "") for l in result["labels"]]

        return {
            "pictureId": picture_id,
            "isSafe": result.get("safe", True),
            "confidence": result.get("confidence", 1.0 if result.get("safe") else 0.0),
            "provider": result.get("provider", provider or "unknown"),
            "modelVersion": "1.0",
            "violationCategories": violations,
            "processingMs": 0,
            "rawResponse": str(result),
        }
