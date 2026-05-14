import hashlib
import logging
import numpy as np

from app.models.registry import get_registry
from app.infrastructure.cache import get_cache

logger = logging.getLogger(__name__)

CACHE_TTL = 86400


class EmbeddingService:
    async def embed_text(self, text: str) -> tuple[list[float], bool]:
        cache_key = self._cache_key(text, "text")
        cache = get_cache()

        cached = await cache.get_json(cache_key)
        if cached is not None:
            return cached, True

        registry = get_registry()
        model = registry.get_clip()
        vector: np.ndarray = model.embed_text(text)
        result = vector.tolist()

        await cache.set_json(cache_key, result, ttl=CACHE_TTL)
        return result, False

    async def embed_image(self, image_url: str) -> tuple[list[float], bool]:
        cache_key = self._cache_key(image_url, "image")
        cache = get_cache()

        cached = await cache.get_json(cache_key)
        if cached is not None:
            return cached, True

        registry = get_registry()
        model = registry.get_clip()
        vector: np.ndarray = model.embed_image_from_url(image_url)
        result = vector.tolist()

        await cache.set_json(cache_key, result, ttl=CACHE_TTL)
        return result, False

    @staticmethod
    def _cache_key(content: str, content_type: str) -> str:
        return f"ai:emb:{content_type}:{hashlib.md5(content.encode()).hexdigest()}"


embedding_service = EmbeddingService()
