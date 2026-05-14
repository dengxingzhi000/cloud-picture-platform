import asyncio
import logging
from typing import Optional, Any
import httpx

logger = logging.getLogger(__name__)


class HttpClientManager:
    def __init__(self, timeout: float = 30.0, max_connections: int = 100):
        self._timeout = timeout
        self._max_connections = max_connections
        self._client: Optional[httpx.AsyncClient] = None

    async def get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            limits = httpx.Limits(max_connections=self._max_connections)
            self._client = httpx.AsyncClient(timeout=self._timeout, limits=limits)
        return self._client

    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()
            self._client = None
            logger.info("HTTP client closed")

    async def get(self, url: str, **kwargs) -> httpx.Response:
        client = await self.get_client()
        return await client.get(url, **kwargs)

    async def post(self, url: str, **kwargs) -> httpx.Response:
        client = await self.get_client()
        return await client.post(url, **kwargs)


_http: Optional[HttpClientManager] = None


def get_http() -> HttpClientManager:
    if _http is None:
        raise RuntimeError("HTTP client not initialized")
    return _http


def init_http(timeout: float = 30.0, max_connections: int = 100) -> HttpClientManager:
    global _http
    _http = HttpClientManager(timeout=timeout, max_connections=max_connections)
    return _http
