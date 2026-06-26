import asyncio
import hashlib
import json
import logging
from typing import Optional

import httpx

from app.config import get_config
from app.models.registry import get_registry
from app.infrastructure.cache import get_cache

logger = logging.getLogger(__name__)

GENERAL_LABELS = [
    "person", "animal", "landscape", "food", "vehicle", "building",
    "document", "screenshot", "artwork", "product", "nature", "city",
    "indoor", "outdoor", "text", "face", "group", "technology",
]

DEEPSEEK_TAG_PROMPT = """你是一个图片标签生成专家。根据用户提供的图片描述，生成合适的分类标签。

要求：
- 返回5-15个相关标签
- 标签应涵盖：主体、场景、氛围、风格、用途
- 使用中文标签
- 每个标签附带置信度（0.0-1.0）

请严格以JSON格式回复，不要包含其他内容：
{"tags": [{"label": "标签名", "confidence": 0.95}, {"label": "标签名2", "confidence": 0.88}]}"""

DEEPSEEK_COMBINED_PROMPT = """你是一个图片标签生成专家。根据以下三路分析结果，生成最终的图片标签。

CLIP分类结果：{clip_tags}
图像描述：{description}
目标检测物体：{detections}

要求：
- 综合三路信息，去重合并
- 返回5-15个相关标签
- 标签应涵盖：主体、场景、氛围、风格、用途
- 使用中文标签
- 每个标签附带置信度（0.0-1.0）
- 优先使用目标检测和CLIP中的明确标签，描述用于补充氛围和风格类标签

请严格以JSON格式回复，不要包含其他内容：
{"tags": [{"label": "标签名", "confidence": 0.95}, {"label": "标签名2", "confidence": 0.88}]}"""

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

    async def tag_by_description(
        self,
        description: str,
        picture_id: Optional[str] = None,
    ) -> tuple[list[dict], bool]:
        cache_key = f"ai:tag_desc:{hashlib.md5(description.encode()).hexdigest()}"
        cache = get_cache()

        cached = await cache.get_json(cache_key)
        if cached is not None:
            return cached, True

        config = get_config()
        if not config.deepseek_api_key:
            logger.warning("DeepSeek API key not configured for description tagging")
            return [], False

        messages = [
            {"role": "system", "content": DEEPSEEK_TAG_PROMPT},
            {"role": "user", "content": f"图片描述：{description}"},
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
                        "temperature": 0.3,
                        "max_tokens": 1024,
                    },
                )
                resp.raise_for_status()
                data = resp.json()

            content = data["choices"][0]["message"]["content"]
            content = content.strip()
            if content.startswith("```"):
                lines = content.split("\n", 1)
                content = lines[1].rsplit("```", 1)[0].strip() if len(lines) > 1 else content[3:-3].strip()

            result = json.loads(content)
            tags = result.get("tags", [])
            tags.sort(key=lambda x: x.get("confidence", 0), reverse=True)

            await cache.set_json(cache_key, tags, ttl=CACHE_TTL)
            return tags, False

        except Exception as e:
            logger.exception("DeepSeek description tagging failed")
            return [], False

    @staticmethod
    def _cache_key(image_url: str, custom_labels: Optional[list[str]] = None) -> str:
        raw = f"tag:{image_url}"
        if custom_labels:
            raw += f":{','.join(sorted(custom_labels))}"
        return f"ai:tag:{hashlib.md5(raw.encode()).hexdigest()}"

    async def tag_image_combined(
        self,
        image_url: str,
        top_k: int = 5,
        custom_labels: Optional[list[str]] = None,
    ) -> tuple[list[dict], str, list[str], bool]:
        cache_key = f"ai:tag_combined:{hashlib.md5(image_url.encode()).hexdigest()}"
        cache = get_cache()
        cached = await cache.get_json(cache_key)
        if cached is not None:
            return (
                cached.get("tags", []),
                cached.get("description", ""),
                cached.get("detections", []),
                True,
            )

        registry = get_registry()
        labels = custom_labels or GENERAL_LABELS

        def _clip_sync():
            model = registry.get_clip()
            results = model.classify_image(image_url, labels)
            results.sort(key=lambda x: x["confidence"], reverse=True)
            return results[:top_k]

        def _florence_sync():
            model = registry.get_florence()
            if not model.is_ready():
                return ""
            return model.describe_image(image_url)

        def _yolo_sync():
            model = registry.get_yolo()
            if not model.is_ready():
                return []
            return model.get_object_names(image_url)

        clip_tags, description, detections = await asyncio.gather(
            asyncio.to_thread(_clip_sync),
            asyncio.to_thread(_florence_sync),
            asyncio.to_thread(_yolo_sync),
        )

        config = get_config()
        if config.deepseek_api_key:
            tags = await self._deepseek_combined_tag(
                clip_tags, description, detections
            )
        else:
            tags = clip_tags

        tags.sort(key=lambda x: x.get("confidence", 0), reverse=True)

        cache_data = {
            "tags": tags,
            "description": description,
            "detections": detections,
        }
        await cache.set_json(cache_key, cache_data, ttl=CACHE_TTL)
        return tags, description, detections, False

    async def _deepseek_combined_tag(
        self,
        clip_tags: list[dict],
        description: str,
        detections: list[str],
    ) -> list[dict]:
        config = get_config()
        clip_str = json.dumps(
            [{"label": t["label"], "score": round(t["confidence"], 2)} for t in clip_tags],
            ensure_ascii=False,
        ) if clip_tags else "无"
        desc_str = description if description else "无"
        detect_str = ", ".join(detections) if detections else "无"

        prompt = DEEPSEEK_COMBINED_PROMPT.format(
            clip_tags=clip_str,
            description=desc_str,
            detections=detect_str,
        )

        messages = [
            {"role": "system", "content": prompt},
            {"role": "user", "content": "请根据以上分析结果生成图片标签。"},
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
                        "temperature": 0.3,
                        "max_tokens": 1024,
                    },
                )
                resp.raise_for_status()
                data = resp.json()

            content = data["choices"][0]["message"]["content"]
            content = content.strip()
            if content.startswith("```"):
                lines = content.split("\n", 1)
                content = lines[1].rsplit("```", 1)[0].strip() if len(lines) > 1 else content[3:-3].strip()

            result = json.loads(content)
            return result.get("tags", [])
        except Exception:
            logger.exception("DeepSeek combined tagging failed")
            return clip_tags


tagging_service = TaggingService()
