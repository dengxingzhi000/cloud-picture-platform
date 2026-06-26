import json
import logging
from typing import Optional

import httpx

from app.config import get_config

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """你是一个云图平台的AI助手。你可以通过调用工具来搜索和查询用户的图片资源。

规则：
- 当用户询问关于图片的问题时，必须先调用搜索工具获取真实数据，不要编造任何图片信息
- 使用中文回复，简洁明了
- 搜索结果要基于工具返回的真实数据进行总结"""

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_pictures",
            "description": "搜索平台中的图片，支持按关键词、标签、可见性、排序方式等筛选",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {
                        "type": "string",
                        "description": "搜索关键词，匹配图片名称和文件名"
                    },
                    "tag": {
                        "type": "string",
                        "description": "按标签名称筛选"
                    },
                    "visibility": {
                        "type": "string",
                        "enum": ["PUBLIC", "PRIVATE", "TEAM"],
                        "description": "按可见性筛选"
                    },
                    "orientation": {
                        "type": "string",
                        "enum": ["LANDSCAPE", "PORTRAIT", "SQUARE"],
                        "description": "按图片方向筛选"
                    },
                    "sortBy": {
                        "type": "string",
                        "enum": ["createdAt", "updatedAt", "sizeBytes"],
                        "description": "排序字段，默认按创建时间"
                    },
                    "sortDir": {
                        "type": "string",
                        "enum": ["asc", "desc"],
                        "description": "排序方向，默认降序"
                    },
                    "page": {
                        "type": "integer",
                        "description": "页码，从0开始",
                        "default": 0
                    },
                    "size": {
                        "type": "integer",
                        "description": "每页数量，默认20",
                        "default": 20
                    }
                },
                "required": []
            }
        }
    }
]


class AssistantService:
    def __init__(self):
        self._conversation_cache: dict[str, list[dict]] = {}

    async def chat(
        self,
        message: str,
        session_id: str = "default",
        context_picture_ids: Optional[list[str]] = None,
    ) -> dict:
        config = get_config()
        if not config.deepseek_api_key:
            logger.warning("DeepSeek API key not configured")
            return {
                "reply": "AI助手未配置，请联系管理员设置DeepSeek API密钥。",
                "intent": "error",
                "suggested_actions": [],
            }

        history = self._get_history(session_id)
        messages: list[dict] = [{"role": "system", "content": SYSTEM_PROMPT}]

        if context_picture_ids:
            messages.append({
                "role": "system",
                "content": f"当前上下文中的图片ID: {', '.join(context_picture_ids)}",
            })

        messages.extend(history)
        messages.append({"role": "user", "content": message})

        try:
            result = await self._chat_with_tools(
                messages, config.deepseek_api_key,
                config.deepseek_base_url, config.deepseek_model,
                config.java_backend_url, session_id,
            )
        except Exception as e:
            logger.exception("DeepSeek chat failed")
            return {
                "reply": f"AI服务暂时不可用: {str(e)}",
                "intent": "error",
                "suggested_actions": [],
            }

        self._append_message(session_id, {"role": "user", "content": message})
        self._append_message(session_id, {"role": "assistant", "content": result["reply"]})

        return result

    async def _chat_with_tools(
        self,
        messages: list[dict],
        api_key: str,
        base_url: str,
        model: str,
        java_backend_url: str,
        session_id: str = "default",
    ) -> dict:
        url = f"{base_url}/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": model,
            "messages": messages,
            "tools": TOOLS,
            "tool_choice": "auto",
            "temperature": 0.7,
            "max_tokens": 2048,
        }

        max_rounds = 5
        total_tokens = 0
        tool_call_count = 0

        for _ in range(max_rounds):
            async with httpx.AsyncClient(timeout=60) as client:
                resp = await client.post(url, headers=headers, json=payload)
                resp.raise_for_status()
                data = resp.json()

            usage = data.get("usage", {})
            total_tokens += usage.get("total_tokens", 0)

            choice = data["choices"][0]
            message = choice["message"]

            if message.get("tool_calls"):
                messages.append(message)

                for tool_call in message["tool_calls"]:
                    tool_call_count += 1
                    func_name = tool_call["function"]["name"]
                    try:
                        func_args = json.loads(tool_call["function"]["arguments"])
                    except json.JSONDecodeError:
                        func_args = {}

                    tool_result = await self._execute_tool(
                        func_name, func_args, java_backend_url, session_id
                    )

                    messages.append({
                        "role": "tool",
                        "tool_call_id": tool_call["id"],
                        "content": json.dumps(tool_result, ensure_ascii=False),
                    })

                payload["messages"] = messages
                continue

            content = message.get("content", "")
            intent = self._extract_intent(content)
            actions = self._extract_actions(content)
            return {
                "reply": content,
                "intent": intent,
                "suggested_actions": actions,
                "tokensUsed": total_tokens,
                "toolCallsCount": tool_call_count,
            }

        return {
            "reply": "AI处理轮次超限，请简化您的请求后重试。",
            "intent": "error",
            "suggested_actions": [],
            "tokensUsed": total_tokens,
            "toolCallsCount": tool_call_count,
        }

    async def _execute_tool(
        self, tool_name: str, args: dict, java_backend_url: str, session_id: str = "default"
    ) -> dict:
        if tool_name == "search_pictures":
            return await self._search_pictures(args, java_backend_url, session_id)
        return {"error": f"未知工具: {tool_name}"}

    async def _search_pictures(self, args: dict, java_backend_url: str, session_id: str = "default") -> dict:
        params: dict = {"page": 0, "size": 20, "sessionId": session_id}
        for key in ["keyword", "tag", "visibility", "orientation", "sortBy", "sortDir", "page", "size"]:
            if key in args and args[key] is not None:
                params[key] = args[key]

        try:
            async with httpx.AsyncClient(timeout=10) as client:
                resp = await client.get(
                    f"{java_backend_url}/api/v1/ai/tools/search-pictures",
                    params=params,
                )
                resp.raise_for_status()
                return resp.json()
        except Exception as e:
            logger.warning("Search pictures tool failed: %s", e)
            return {"error": f"搜索失败: {str(e)}"}

    def _extract_intent(self, content: str) -> str:
        content_lower = content.lower()
        if any(kw in content_lower for kw in ["搜索", "查找", "找", "筛选", "search"]):
            return "search"
        if any(kw in content_lower for kw in ["删除", "移除", "批量", "delete"]):
            return "batch_operation"
        if any(kw in content_lower for kw in ["标签", "分类", "tag", "label"]):
            return "tagging"
        if any(kw in content_lower for kw in ["上传", "导入", "upload"]):
            return "upload"
        return "chat"

    def _extract_actions(self, content: str) -> list[str]:
        actions = []
        if "搜索" in content or "查找" in content:
            actions.append("search_pictures")
        if "删除" in content:
            actions.append("delete_pictures")
        if "标签" in content:
            actions.append("manage_tags")
        return actions

    def _get_history(self, session_id: str) -> list[dict]:
        return self._conversation_cache.get(session_id, [])

    def _append_message(self, session_id: str, message: dict) -> None:
        if session_id not in self._conversation_cache:
            self._conversation_cache[session_id] = []
        self._conversation_cache[session_id].append(message)
        if len(self._conversation_cache[session_id]) > 20:
            self._conversation_cache[session_id] = \
                self._conversation_cache[session_id][-20:]

    async def get_history(self, session_id: str) -> list[dict]:
        return self._get_history(session_id)


assistant_service = AssistantService()
