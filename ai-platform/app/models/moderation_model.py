import time
import json
import logging
from typing import Optional
import httpx

from app.models.base import BaseAiModel, ModelInfo

logger = logging.getLogger(__name__)

ALIYUN_MODERATION_URL = "https://green-cip.cn-beijing.aliyuncs.com/sgreen/msg/v1/textmod"

DEEPSEEK_MODERATION_PROMPT = """你是一个内容审核专家。请判断以下文本内容是否违规。

审核维度：
- 暴力血腥
- 色情低俗
- 政治敏感
- 违法信息
- 仇恨歧视
- 欺诈诈骗
- 未成年人保护

请严格以JSON格式回复，不要包含其他内容：
{"safe": true/false, "confidence": 0.0-1.0, "violations": ["类别1", "类别2"], "reason": "简要说明"}"""


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
        if self._provider == "deepseek":
            return await self._moderate_deepseek(text)
        return await self._moderate_openai(text)

    async def moderate_image(self, image_url: str) -> dict:
        if self._provider == "aliyun":
            return await self._moderate_aliyun_image(image_url)
        return {"safe": True, "provider": self._provider, "note": "image moderation not implemented"}

    async def _moderate_deepseek(self, text: str) -> dict:
        from app.config import get_config

        config = get_config()
        if not config.deepseek_api_key:
            return {"safe": True, "provider": "deepseek", "note": "api key not configured"}

        messages = [
            {"role": "system", "content": DEEPSEEK_MODERATION_PROMPT},
            {"role": "user", "content": text},
        ]

        try:
            async with httpx.AsyncClient(timeout=30) as client:
                resp = await client.post(
                    f"{config.deepseek_base_url}/v1/chat/completions",
                    headers={
                        "Authorization": f"Bearer {config.deepseek_api_key}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": config.deepseek_model,
                        "messages": messages,
                        "temperature": 0.1,
                        "max_tokens": 512,
                    },
                )
                resp.raise_for_status()
                data = resp.json()

            content = data["choices"][0]["message"]["content"]
            content = content.strip()
            if content.startswith("```"):
                content = content.split("\n", 1)[1].rsplit("```", 1)[0].strip()

            result = json.loads(content)
            return {
                "safe": result.get("safe", True),
                "confidence": result.get("confidence", 0.0),
                "violations": result.get("violations", []),
                "reason": result.get("reason", ""),
                "provider": "deepseek",
            }
        except Exception as e:
            logger.exception("DeepSeek moderation failed")
            return {"safe": True, "provider": "deepseek", "error": str(e)}

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
