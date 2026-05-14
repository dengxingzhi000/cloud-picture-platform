import hashlib
import logging
from typing import Optional

from app.models.registry import get_registry
from app.infrastructure.cache import get_cache

logger = logging.getLogger(__name__)

CACHE_TTL = 3600


class ModerationService:
    async def moderate_text(self, text: str, provider: Optional[str] = None) -> tuple[dict, bool]:
        cache_key = self._cache_key(text, "text")
        cache = get_cache()

        cached = await cache.get_json(cache_key)
        if cached is not None:
            return cached, True

        registry = get_registry()
        model = registry.get_moderation()
        result = await model.moderate_text(text)

        await cache.set_json(cache_key, result, ttl=CACHE_TTL)
        return result, False

    async def moderate_image(self, image_url: str, provider: Optional[str] = None) -> tuple[dict, bool]:
        cache_key = self._cache_key(image_url, "image")
        cache = get_cache()

        cached = await cache.get_json(cache_key)
        if cached is not None:
            return cached, True

        registry = get_registry()
        model = registry.get_moderation()
        result = await model.moderate_image(image_url)

        await cache.set_json(cache_key, result, ttl=CACHE_TTL)
        return result, False

    @staticmethod
    def _cache_key(content: str, content_type: str) -> str:
        return f"ai:mod:{content_type}:{hashlib.md5(content.encode()).hexdigest()}"


moderation_service = ModerationService()
