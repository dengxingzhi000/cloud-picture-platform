import hashlib
import logging
from typing import Optional

from app.models.registry import get_registry
from app.infrastructure.cache import get_cache

logger = logging.getLogger(__name__)

GENERAL_LABELS = [
    "person", "animal", "landscape", "food", "vehicle", "building",
    "document", "screenshot", "artwork", "product", "nature", "city",
    "indoor", "outdoor", "text", "face", "group", "technology",
]

CACHE_TTL = 86400


class TaggingService:
    async def tag_image(
        self,
        image_url: str,
        top_k: int = 5,
        custom_labels: Optional[list[str]] = None,
    ) -> tuple[list[dict], bool]:
        cache_key = self._cache_key(image_url, custom_labels)
        cache = get_cache()

        cached = await cache.get_json(cache_key)
        if cached is not None:
            logger.info("Tagging cache hit for %s", image_url)
            return cached, True

        labels = custom_labels or GENERAL_LABELS
        registry = get_registry()
        model = registry.get_clip()
        results = model.classify_image(image_url, labels)
        results.sort(key=lambda x: x["confidence"], reverse=True)
        top = results[:top_k]

        await cache.set_json(cache_key, top, ttl=CACHE_TTL)
        return top, False

    @staticmethod
    def _cache_key(image_url: str, custom_labels: Optional[list[str]] = None) -> str:
        raw = f"tag:{image_url}"
        if custom_labels:
            raw += f":{','.join(sorted(custom_labels))}"
        return f"ai:tag:{hashlib.md5(raw.encode()).hexdigest()}"


tagging_service = TaggingService()
