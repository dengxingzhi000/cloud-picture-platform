import json
import logging
from typing import Optional, Any
import redis.asyncio as aioredis

logger = logging.getLogger(__name__)

DEFAULT_TTL = 3600


class CacheManager:
    def __init__(self, url: str):
        self._url = url
        self._client: Optional[aioredis.Redis] = None

    async def connect(self):
        if self._client is None:
            self._client = aioredis.from_url(self._url, decode_responses=True)
            await self._client.ping()
            logger.info("Connected to Redis")

    async def close(self):
        if self._client:
            await self._client.aclose()
            self._client = None
            logger.info("Disconnected from Redis")

    async def get(self, key: str) -> Optional[str]:
        if self._client is None:
            return None
        try:
            return await self._client.get(key)
        except Exception as e:
            logger.warning("Redis get failed: %s", e)
            return None

    async def get_json(self, key: str) -> Optional[Any]:
        val = await self.get(key)
        if val is None:
            return None
        try:
            return json.loads(val)
        except json.JSONDecodeError:
            return None

    async def set(self, key: str, value: str, ttl: int = DEFAULT_TTL):
        if self._client is None:
            return
        try:
            await self._client.setex(key, ttl, value)
        except Exception as e:
            logger.warning("Redis set failed: %s", e)

    async def set_json(self, key: str, value: Any, ttl: int = DEFAULT_TTL):
        await self.set(key, json.dumps(value, ensure_ascii=False), ttl=ttl)

    async def delete(self, key: str):
        if self._client is None:
            return
        try:
            await self._client.delete(key)
        except Exception as e:
            logger.warning("Redis delete failed: %s", e)

    async def exists(self, key: str) -> bool:
        if self._client is None:
            return False
        try:
            return await self._client.exists(key) > 0
        except Exception as e:
            logger.warning("Redis exists failed: %s", e)
            return False


_cache: Optional[CacheManager] = None


def get_cache() -> CacheManager:
    if _cache is None:
        raise RuntimeError("Cache not initialized")
    return _cache


def init_cache(url: str) -> CacheManager:
    global _cache
    _cache = CacheManager(url)
    return _cache
