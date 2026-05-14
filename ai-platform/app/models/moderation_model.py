import time
import json
import logging
from typing import Optional
import httpx

from app.models.base import BaseAiModel, ModelInfo

logger = logging.getLogger(__name__)

ALIYUN_MODERATION_URL = "https://green-cip.cn-beijing.aliyuncs.com/sgreen/msg/v1/textmod"


class ModerationModel(BaseAiModel):
    def __init__(self, provider: str = "aliyun", aliyun_ak: str = "", aliyun_sk: str = ""):
        self._provider = provider
        self._aliyun_ak = aliyun_ak
        self._aliyun_sk = aliyun_sk
        self._ready = True

    @property
    def model_info(self) -> ModelInfo:
        return ModelInfo(
            name=f"moderation-{self._provider}",
            version="1.0",
            provider=self._provider,
            loaded_at=time.time(),
            device="api",
        )

    def is_ready(self) -> bool:
        return self._ready

    async def moderate_text(self, text: str) -> dict:
        if self._provider == "aliyun":
            return await self._moderate_aliyun(text)
        return await self._moderate_openai(text)

    async def moderate_image(self, image_url: str) -> dict:
        if self._provider == "aliyun":
            return await self._moderate_aliyun_image(image_url)
        return {"safe": True, "provider": self._provider, "note": "image moderation not implemented"}

    async def _moderate_aliyun(self, text: str) -> dict:
        import hmac
        import hashlib
        import base64
        from urllib.parse import urlencode

        timestamp = str(int(time.time() * 1000))
        params = {
            "action": "TextModeration",
            "version": "2022-09-01",
            "timestamp": timestamp,
            "accessKey": self._aliyun_ak,
        }
        sorted_params = "&".join(f"{k}={v}" for k, v in sorted(params.items()))
        sign_str = f"POST\n{urlencode(sorted_params)}\n{json.dumps({'content': text}, ensure_ascii=False)}"
        signature = base64.b64encode(
            hmac.new(self._aliyun_sk.encode(), sign_str.encode(), hashlib.sha256).digest()
        ).decode()
        params["signature"] = signature

        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(ALIYUN_MODERATION_URL, params=params, json={"content": text})
            data = resp.json()

        suggestion = data.get("Data", {}).get("Suggestion", "pass")
        labels = data.get("Data", {}).get("Labels", [])
        return {
            "safe": suggestion == "pass",
            "suggestion": suggestion,
            "labels": labels,
            "provider": "aliyun",
        }

    async def _moderate_openai(self, text: str) -> dict:
        from app.config import get_config

        config = get_config()
        api_key = config.openai_api_key if hasattr(config, "openai_api_key") else ""

        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                "https://api.openai.com/v1/moderations",
                headers={"Authorization": f"Bearer {api_key}"},
                json={"input": text},
            )
            data = resp.json()

        results = data.get("results", [{}])[0]
        flagged = results.get("flagged", False)
        categories = results.get("categories", {})
        return {
            "safe": not flagged,
            "flagged": flagged,
            "categories": categories,
            "provider": "openai",
        }

    async def _moderate_aliyun_image(self, image_url: str) -> dict:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                "https://green-cip.cn-beijing.aliyuncs.com/sgreen/msg/v1/imagemod",
                json={"imageUrl": image_url},
            )
            data = resp.json()
        suggestion = data.get("Data", {}).get("Suggestion", "pass")
        return {
            "safe": suggestion == "pass",
            "suggestion": suggestion,
            "provider": "aliyun",
        }
