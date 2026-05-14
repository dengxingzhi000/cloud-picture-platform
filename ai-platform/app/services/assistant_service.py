import logging
from typing import Optional

logger = logging.getLogger(__name__)


class AssistantService:
    async def chat(self, message: str, session_id: Optional[str] = None) -> str:
        logger.info("Chat stub: session=%s msg=%s", session_id, message[:50])
        return f"Echo: {message}"

    async def get_history(self, session_id: str) -> list[dict]:
        logger.info("History stub: session=%s", session_id)
        return []


assistant_service = AssistantService()
