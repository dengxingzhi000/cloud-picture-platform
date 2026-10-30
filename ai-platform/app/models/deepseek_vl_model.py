import json
import time
import logging
from typing import Optional

import httpx

from app.models.base import BaseAiModel, ModelInfo
from app.config import get_config

logger = logging.getLogger(__name__)

DESCRIBE_PROMPT = "请详细描述这张图片的内容，包括主体、场景、颜色、风格等视觉元素。"

KEYWORD_PROMPT = "请分析这张图片，输出5-15个中文关键词标签，仅返回JSON数组格式：[\"标签1\", \"标签2\"]"


class DeepSeekVlModel(BaseAiModel):
    def __init__(self, model_name: str = "deepseek-v4-flash"):
        self._model_name = model_name
        self._loaded = True

    def load(self):
        self._loaded = True

    @property
    def model_info(self) -> ModelInfo:
        return ModelInfo(
            name=self._model_name,
            version="4.0",
            provider="deepseek",
            loaded_at=time.time() if self._loaded else 0,
            device="api",
        )

    def is_ready(self) -> bool:
        return self._loaded

    async def _call_api(self, prompt: str, image_url: str) -> str:
        config = get_config()
        if not config.deepseek_api_key:
            logger.warning("DeepSeek API key not configured for VL model")
            return ""

        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image_url", "image_url": {"url": image_url}},
                    {"type": "text", "text": prompt},
                ],
            }
        ]

        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(
                f"{config.deepseek_base_url}/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {config.deepseek_api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self._model_name,
                    "messages": messages,
                    "temperature": 0.3,
                    "max_tokens": 1024,
                    "thinking": {"type": "disabled"},
                },
            )
            resp.raise_for_status()
            data = resp.json()

        return data["choices"][0]["message"]["content"]

    async def describe_image(self, image_url: str, prompt: Optional[str] = None) -> str:
        logger.info("DeepSeek-VL describing image: %s", image_url)
        return await self._call_api(prompt or DESCRIBE_PROMPT, image_url)

    async def extract_keywords(self, image_url: str) -> list[str]:
        logger.info("DeepSeek-VL extracting keywords from: %s", image_url)
        content = await self._call_api(KEYWORD_PROMPT, image_url)
        if not content:
            return ["image"]

        content = content.strip()
        if content.startswith("```"):
            lines = content.split("\n", 1)
            content = lines[1].rsplit("```", 1)[0].strip() if len(lines) > 1 else content[3:-3].strip()

        try:
            return json.loads(content)
        except (json.JSONDecodeError, TypeError):
            logger.warning("Failed to parse keywords from DeepSeek-VL response")
            return ["image"]
