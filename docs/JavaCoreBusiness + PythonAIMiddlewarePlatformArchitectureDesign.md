# Java 主业务 + Python AI 中台架构设计方案

## 架构决策背景

当前平台基于 Spring Boot 4 + Java 21，AI 能力如果直接内嵌 Java 生态（DJL、OpenNLP 等），面临以下问题：

- Python AI 生态（PyTorch、Transformers、CLIP）远比 Java 成熟
- AI 模型迭代与业务迭代耦合，互相干扰发布节奏
- AI 团队和业务团队技术栈割裂，协作成本高
- Java 调用 Python 模型库的 JNI/Jython 方案维护成本极高

**核心决策：Java 主业务 + Python AI 中台，通过 HTTP/gRPC + 消息队列解耦**

---

## 一、整体架构设计

### 1.1 系统全景图

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端层                                  │
│              Web / Mobile / Open API / Webhook                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTPS
┌──────────────────────────▼──────────────────────────────────────┐
│                    Java 主业务层（现有）                          │
│                                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │  Picture     │  │   Team      │  │    Admin / RBAC         │  │
│  │  Service     │  │   Service   │  │    Service              │  │
│  └──────┬──────┘  └─────────────┘  └─────────────────────────┘  │
│         │                                                         │
│  ┌──────▼──────────────────────────────────────────────────────┐ │
│  │              AI Gateway（Java 侧统一出口）                   │ │
│  │   - 请求路由  - 降级熔断  - 调用审计  - 费用计量            │ │
│  └──────┬───────────────────┬────────────────────────────────┘  │
│         │ 同步 HTTP/gRPC    │ 异步消息                           │
└─────────┼───────────────────┼───────────────────────────────────┘
          │                   │
┌─────────▼───────────────────▼──────────────────────────────────┐
│                    Python AI 中台层（新建）                       │
│                                                                   │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌─────────────┐  │
│  │  Tagging   │ │ Moderation │ │  Embedding │ │  Assistant  │  │
│  │  Service   │ │  Service   │ │  Service   │ │  Service    │  │
│  └────────────┘ └────────────┘ └────────────┘ └─────────────┘  │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              Model Registry（模型仓库）                      │ │
│  │     CLIP / Qwen-VL / 内容安全模型 / 自训练分类模型          │ │
│  └─────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
          │
┌─────────▼──────────────────────────────────────────────────────┐
│                       基础设施层                                  │
│   PostgreSQL + pgvector │ Redis │ RabbitMQ/Kafka │ MinIO/OSS    │
└────────────────────────────────────────────────────────────────┘
```

### 1.2 通信模式选择

```
场景                        通信方式          理由
─────────────────────────────────────────────────────
用户等待的实时请求           同步 HTTP         用户体验，需要立即返回
  （如：搜索、以图搜图）

上传后自动处理               异步消息队列      不阻塞上传，允许重试
  （如：自动打标、审核）

批量任务                     异步消息队列      削峰填谷，资源利用率高
  （如：批量重新打标）

AI 中台内部服务调用           gRPC             高性能，强类型契约
```

---

## 二、Java 侧改造设计

### 2.1 AI Gateway 模块

新增 `infrastructure/ai/` 包，作为 Java 侧所有 AI 调用的统一出口：

```
src/main/java/.../infrastructure/ai/
├── gateway/
│   ├── AiGateway.java                  # 统一网关接口
│   ├── AiGatewayImpl.java              # 实现（路由、熔断、审计）
│   └── AiGatewayProperties.java        # 配置
├── client/
│   ├── TaggingClient.java              # HTTP 客户端：打标服务
│   ├── ModerationClient.java           # HTTP 客户端：审核服务
│   ├── EmbeddingClient.java            # HTTP 客户端：向量服务
│   └── AssistantClient.java            # HTTP 客户端：助手服务
├── dto/
│   ├── TaggingRequest.java
│   ├── TaggingResponse.java
│   ├── ModerationRequest.java
│   ├── ModerationResponse.java
│   ├── EmbeddingRequest.java
│   ├── EmbeddingResponse.java
│   └── AiTaskResult.java               # 通用异步任务结果
├── fallback/
│   ├── TaggingFallback.java            # 降级：返回空标签
│   └── ModerationFallback.java         # 降级：转人工审核
└── audit/
    └── AiCallAuditService.java         # 调用记录
```

**AiGateway 核心接口设计：**

```java
// infrastructure/ai/gateway/AiGateway.java

public interface AiGateway {

    /**
     * 同步调用：语义向量化（用于搜索）
     * 超时 5s，失败降级到关键词搜索
     */
    Optional<float[]> embedText(String text);

    /**
     * 同步调用：以图搜图向量化
     * 超时 10s，失败返回空结果
     */
    Optional<float[]> embedImage(String imageUrl);

    /**
     * 异步调用：图片自动打标
     * 发消息到队列，结果异步回调
     */
    void submitTaggingTask(UUID pictureId, String imageUrl);

    /**
     * 异步调用：内容审核
     * 发消息到队列，结果异步写库
     */
    void submitModerationTask(UUID pictureId, String imageUrl);

    /**
     * 同步调用：对话助手
     * 超时 30s，失败返回错误提示
     */
    AiChatResponse chat(AiChatRequest request);
}
```

**熔断降级配置（使用 Resilience4j）：**

```java
// infrastructure/ai/gateway/AiGatewayImpl.java

@Service
public class AiGatewayImpl implements AiGateway {

    private final TaggingClient taggingClient;
    private final EmbeddingClient embeddingClient;
    private final RabbitTemplate rabbitTemplate;
    private final AiCallAuditService auditService;

    // 熔断器：AI 中台不可用时自动降级
    @CircuitBreaker(name = "ai-embedding", fallbackMethod = "embedTextFallback")
    @TimeLimiter(name = "ai-embedding")
    @Override
    public Optional<float[]> embedText(String text) {
        long startMs = System.currentTimeMillis();
        try {
            EmbeddingResponse resp = embeddingClient.embedText(
                new EmbeddingRequest(text, "text")
            );
            auditService.record("embedding", "text", true, System.currentTimeMillis() - startMs);
            return Optional.of(resp.getVector());
        } catch (Exception e) {
            auditService.record("embedding", "text", false, System.currentTimeMillis() - startMs);
            throw e;
        }
    }

    // 降级方法：返回 empty，调用方切回关键词搜索
    private Optional<float[]> embedTextFallback(String text, Throwable t) {
        log.warn("AI embedding fallback triggered: {}", t.getMessage());
        return Optional.empty();
    }

    @Override
    public void submitTaggingTask(UUID pictureId, String imageUrl) {
        // 发送到消息队列，不等待结果
        AiTaskMessage message = AiTaskMessage.builder()
            .taskId(UUID.randomUUID())
            .taskType("IMAGE_TAGGING")
            .pictureId(pictureId)
            .imageUrl(imageUrl)
            .submittedAt(Instant.now())
            .build();

        rabbitTemplate.convertAndSend(
            "ai.exchange",
            "ai.tagging.submit",
            message
        );

        auditService.recordSubmit("tagging", pictureId);
    }
}
```

### 2.2 AI 任务结果消费

Python AI 中台处理完成后，将结果写回消息队列，Java 侧消费：

```java
// infrastructure/ai/consumer/

@Component
public class AiTaggingResultConsumer {

    private final PictureTagService pictureTagService;
    private final AiCallAuditService auditService;

    @RabbitListener(queues = "ai.tagging.result")
    public void onTaggingResult(AiTaggingResultMessage message) {
        try {
            // 将 AI 打标结果写入数据库
            pictureTagService.applyAiTags(
                message.getPictureId(),
                message.getTags(),          // List<{text, confidence, category}>
                message.getProvider(),      // "clip-vit-large" 等
                message.getModelVersion()
            );
            auditService.recordResult("tagging", message.getPictureId(), true);
        } catch (Exception e) {
            log.error("Failed to apply AI tags: pictureId={}", message.getPictureId(), e);
            auditService.recordResult("tagging", message.getPictureId(), false);
            // 死信队列处理
        }
    }
}

@Component
public class AiModerationResultConsumer {

    private final ModerationService moderationService;

    @RabbitListener(queues = "ai.moderation.result")
    public void onModerationResult(AiModerationResultMessage message) {
        if (message.getConfidence() >= 0.9) {
            // 高置信度：自动处理
            if (message.isSafe()) {
                moderationService.autoApprove(message.getPictureId(), message.getProvider());
            } else {
                moderationService.autoReject(
                    message.getPictureId(),
                    message.getViolationCategories(),
                    message.getProvider()
                );
            }
        } else {
            // 置信度不足：转人工
            moderationService.escalateToHuman(message.getPictureId(), message);
        }
    }
}
```

### 2.3 新增数据库表（Java 侧）

```sql
-- AI 调用审计表
CREATE TABLE ai_call_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type       VARCHAR(50) NOT NULL,   -- tagging/moderation/embedding/chat
    picture_id      UUID,
    user_id         UUID,
    provider        VARCHAR(100),           -- python 中台返回的模型标识
    model_version   VARCHAR(50),
    success         BOOLEAN NOT NULL,
    latency_ms      INTEGER,
    cost_units      INTEGER,                -- 计费单元（token 数 / 图片数）
    error_code      VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_audit_type_created ON ai_call_audit (task_type, created_at);
CREATE INDEX idx_ai_audit_picture ON ai_call_audit (picture_id) WHERE picture_id IS NOT NULL;

-- AI 任务追踪表（异步任务状态）
CREATE TABLE ai_task (
    id              UUID PRIMARY KEY,
    task_type       VARCHAR(50) NOT NULL,
    picture_id      UUID,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING / PROCESSING / COMPLETED / FAILED / CANCELLED
    submitted_at    TIMESTAMPTZ NOT NULL,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT,
    result_summary  JSONB                   -- 简要结果快照
);
```

### 2.4 配置新增

```yaml
# application.yml 新增

app:
  ai:
    enabled: true
    gateway:
      base-url: ${AI_PLATFORM_URL:http://localhost:8000}
      timeout-ms:
        embedding: 5000
        tagging: 30000
        moderation: 15000
        chat: 60000
      circuit-breaker:
        failure-rate-threshold: 50
        wait-duration-seconds: 30
    tagging:
      enabled: true
      min-confidence: 0.65
      max-tags: 20
      trigger: upload         # upload / manual / both
    moderation:
      enabled: true
      auto-approve-threshold: 0.92
      auto-reject-threshold: 0.95
      escalate-to-human: true
    embedding:
      enabled: true
      model: clip-vit-large-patch14
      dimension: 768

  mq:
    ai:
      exchange: ai.exchange
      queues:
        tagging-submit: ai.tagging.submit
        tagging-result: ai.tagging.result
        moderation-submit: ai.moderation.submit
        moderation-result: ai.moderation.result
        embedding-submit: ai.embedding.submit
```

---

## 三、Python AI 中台设计

### 3.1 项目结构

```
ai-platform/
├── pyproject.toml
├── docker-compose.yml
├── Dockerfile
│
├── app/
│   ├── main.py                         # FastAPI 入口
│   ├── config.py                       # 配置管理（pydantic-settings）
│   │
│   ├── api/                            # HTTP 路由层
│   │   ├── v1/
│   │   │   ├── tagging.py
│   │   │   ├── moderation.py
│   │   │   ├── embedding.py
│   │   │   └── assistant.py
│   │   └── health.py
│   │
│   ├── services/                       # 业务逻辑层
│   │   ├── tagging_service.py
│   │   ├── moderation_service.py
│   │   ├── embedding_service.py
│   │   └── assistant_service.py
│   │
│   ├── models/                         # AI 模型封装层
│   │   ├── base.py                     # 基础模型接口
│   │   ├── clip_model.py               # CLIP 模型
│   │   ├── qwen_vl_model.py            # Qwen-VL 多模态
│   │   ├── moderation_model.py         # 内容安全模型
│   │   └── registry.py                 # 模型注册中心
│   │
│   ├── workers/                        # 消息队列消费者
│   │   ├── tagging_worker.py
│   │   ├── moderation_worker.py
│   │   └── base_worker.py
│   │
│   ├── schemas/                        # 数据模型（Pydantic）
│   │   ├── tagging.py
│   │   ├── moderation.py
│   │   ├── embedding.py
│   │   └── common.py
│   │
│   └── infrastructure/
│       ├── mq.py                       # RabbitMQ 连接
│       ├── cache.py                    # Redis 缓存
│       ├── http_client.py              # 调用外部 AI API
│       └── metrics.py                  # Prometheus 指标
│
├── tests/
│   ├── unit/
│   └── integration/
│
└── scripts/
    ├── download_models.py              # 模型下载脚本
    └── benchmark.py                    # 性能基准测试
```

### 3.2 核心模型层设计

```python
# app/models/base.py

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional
import time

@dataclass
class ModelInfo:
    name: str
    version: str
    provider: str           # "local" / "aliyun" / "openai"
    loaded_at: float
    device: str             # "cpu" / "cuda:0"

class BaseAiModel(ABC):
    """所有 AI 模型的基础接口"""

    @property
    @abstractmethod
    def model_info(self) -> ModelInfo:
        pass

    @abstractmethod
    def is_ready(self) -> bool:
        """模型是否已加载就绪"""
        pass

    def health_check(self) -> dict:
        return {
            "model": self.model_info.name,
            "version": self.model_info.version,
            "provider": self.model_info.provider,
            "device": self.model_info.device,
            "ready": self.is_ready()
        }
```

```python
# app/models/clip_model.py

import torch
import open_clip
import numpy as np
from PIL import Image
import requests
from io import BytesIO
from functools import lru_cache
from app.models.base import BaseAiModel, ModelInfo
import time

class ClipModel(BaseAiModel):
    """
    CLIP 模型：图文向量化
    用途：语义搜索、以图搜图、图片分类
    """

    def __init__(self, model_name: str = "ViT-L-14", pretrained: str = "openai"):
        self._model = None
        self._preprocess = None
        self._tokenizer = None
        self._device = "cuda" if torch.cuda.is_available() else "cpu"
        self._model_name = model_name
        self._pretrained = pretrained
        self._loaded_at = None

    def load(self):
        """懒加载：首次使用时才加载模型到显存"""
        if self._model is None:
            self._model, _, self._preprocess = open_clip.create_model_and_transforms(
                self._model_name,
                pretrained=self._pretrained,
                device=self._device
            )
            self._tokenizer = open_clip.get_tokenizer(self._model_name)
            self._model.eval()
            self._loaded_at = time.time()

    @property
    def model_info(self) -> ModelInfo:
        return ModelInfo(
            name=f"clip-{self._model_name.lower()}",
            version=self._pretrained,
            provider="local",
            loaded_at=self._loaded_at or 0,
            device=self._device
        )

    def is_ready(self) -> bool:
        return self._model is not None

    def embed_text(self, text: str) -> np.ndarray:
        """文本向量化"""
        self.load()
        with torch.no_grad():
            tokens = self._tokenizer([text]).to(self._device)
            features = self._model.encode_text(tokens)
            features = features / features.norm(dim=-1, keepdim=True)  # L2 归一化
        return features.cpu().numpy()[0]

    def embed_image_from_url(self, image_url: str) -> np.ndarray:
        """图片 URL 向量化"""
        self.load()
        response = requests.get(image_url, timeout=10)
        image = Image.open(BytesIO(response.content)).convert("RGB")
        return self.embed_image(image)

    def embed_image(self, image: Image.Image) -> np.ndarray:
        """PIL Image 向量化"""
        self.load()
        with torch.no_grad():
            image_tensor = self._preprocess(image).unsqueeze(0).to(self._device)
            features = self._model.encode_image(image_tensor)
            features = features / features.norm(dim=-1, keepdim=True)
        return features.cpu().numpy()[0]

    def classify_image(
        self,
        image_url: str,
        candidate_labels: list[str]
    ) -> list[dict]:
        """
        零样本图片分类
        用于打标签（给定候选标签，计算相似度）
        """
        self.load()
        image_features = self.embed_image_from_url(image_url)
        text_features = np.array([self.embed_text(label) for label in candidate_labels])

        # 余弦相似度
        similarities = (image_features @ text_features.T)

        results = []
        for label, score in zip(candidate_labels, similarities):
            results.append({"label": label, "confidence": float(score)})

        return sorted(results, key=lambda x: x["confidence"], reverse=True)
```

```python
# app/models/registry.py

from typing import Optional
from app.models.clip_model import ClipModel
from app.models.moderation_model import ModerationModel
from app.models.qwen_vl_model import QwenVlModel
import logging

logger = logging.getLogger(__name__)

class ModelRegistry:
    """
    模型注册中心：统一管理所有 AI 模型的生命周期
    - 懒加载：只在需要时才加载模型
    - 热替换：支持不停服更新模型
    - 健康检查：暴露各模型状态
    """

    def __init__(self, config):
        self._config = config
        self._models = {}
        self._initialize()

    def _initialize(self):
        """注册所有模型（不加载，只实例化）"""
        self._models["clip"] = ClipModel(
            model_name=self._config.clip_model_name,
            pretrained=self._config.clip_pretrained
        )
        self._models["moderation"] = ModerationModel(
            provider=self._config.moderation_provider
        )
        if self._config.qwen_vl_enabled:
            self._models["qwen-vl"] = QwenVlModel(
                model_path=self._config.qwen_vl_path
            )

    def get_clip(self) -> ClipModel:
        return self._models["clip"]

    def get_moderation(self) -> ModerationModel:
        return self._models["moderation"]

    def get_qwen_vl(self) -> Optional[QwenVlModel]:
        return self._models.get("qwen-vl")

    def health_report(self) -> dict:
        return {
            name: model.health_check()
            for name, model in self._models.items()
        }

# 单例
_registry: Optional[ModelRegistry] = None

def get_registry() -> ModelRegistry:
    global _registry
    if _registry is None:
        raise RuntimeError("ModelRegistry not initialized")
    return _registry

def init_registry(config) -> ModelRegistry:
    global _registry
    _registry = ModelRegistry(config)
    return _registry
```

### 3.3 服务层设计

```python
# app/services/tagging_service.py

from typing import Optional
import logging
import asyncio
from app.models.registry import get_registry
from app.schemas.tagging import TaggingRequest, TaggingResponse, TagResult
from app.infrastructure.cache import get_cache

logger = logging.getLogger(__name__)

# 预定义标签词库（可从配置/数据库加载）
GENERAL_LABELS = [
    "人物", "风景", "建筑", "食物", "动物", "产品", "图表",
    "截图", "艺术", "运动", "自然", "城市", "室内", "户外",
    "黑白", "夜景", "特写", "全景"
]

class TaggingService:

    def __init__(self):
        self._cache = get_cache()

    async def tag_image(self, request: TaggingRequest) -> TaggingResponse:
        """
        核心打标逻辑：
        1. 检查缓存（相同图片不重复计算）
        2. CLIP 零样本分类（通用标签）
        3. 可选：Qwen-VL 生成描述性标签
        4. 过滤置信度，返回结果
        """
        cache_key = f"tagging:{request.image_url_hash}"
        cached = await self._cache.get(cache_key)
        if cached:
            logger.info("Tagging cache hit: pictureId=%s", request.picture_id)
            return TaggingResponse(**cached)

        registry = get_registry()
        clip = registry.get_clip()

        # 1. 通用标签分类
        general_results = await asyncio.get_event_loop().run_in_executor(
            None,
            lambda: clip.classify_image(request.image_url, GENERAL_LABELS)
        )

        tags = [
            TagResult(
                text=r["label"],
                confidence=r["confidence"],
                category="general",
                source="clip"
            )
            for r in general_results
            if r["confidence"] >= request.min_confidence
        ]

        # 2. 如果启用 Qwen-VL，追加描述性标签
        qwen_vl = registry.get_qwen_vl()
        if qwen_vl and request.enable_descriptive_tags:
            descriptive_tags = await self._get_descriptive_tags(
                qwen_vl, request.image_url
            )
            tags.extend(descriptive_tags)

        # 3. 去重、截断
        seen = set()
        unique_tags = []
        for tag in sorted(tags, key=lambda t: t.confidence, reverse=True):
            if tag.text not in seen and len(unique_tags) < request.max_tags:
                seen.add(tag.text)
                unique_tags.append(tag)

        response = TaggingResponse(
            picture_id=request.picture_id,
            tags=unique_tags,
            provider=clip.model_info.name,
            model_version=clip.model_info.version,
        )

        # 缓存 1 小时（相同图片结果稳定）
        await self._cache.set(cache_key, response.dict(), ttl=3600)
        return response

    async def _get_descriptive_tags(self, qwen_vl, image_url: str) -> list[TagResult]:
        """Qwen-VL 生成描述性标签"""
        try:
            description = await asyncio.get_event_loop().run_in_executor(
                None,
                lambda: qwen_vl.describe_image(image_url)
            )
            # 从描述中提取关键词作为标签
            keywords = qwen_vl.extract_keywords(description)
            return [
                TagResult(text=kw, confidence=0.8, category="descriptive", source="qwen-vl")
                for kw in keywords
            ]
        except Exception as e:
            logger.warning("Qwen-VL tagging failed: %s", e)
            return []
```

```python
# app/services/embedding_service.py

import numpy as np
from app.models.registry import get_registry
from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse
from app.infrastructure.cache import get_cache
import asyncio
import hashlib

class EmbeddingService:

    def __init__(self):
        self._cache = get_cache()

    async def embed(self, request: EmbeddingRequest) -> EmbeddingResponse:
        clip = get_registry().get_clip()

        if request.input_type == "text":
            cache_key = f"embed:text:{hashlib.md5(request.content.encode()).hexdigest()}"
            cached = await self._cache.get(cache_key)
            if cached:
                return EmbeddingResponse(vector=cached, model=clip.model_info.name)

            vector = await asyncio.get_event_loop().run_in_executor(
                None, lambda: clip.embed_text(request.content)
            )
            await self._cache.set(cache_key, vector.tolist(), ttl=86400)

        elif request.input_type == "image":
            cache_key = f"embed:image:{request.content_hash}"
            cached = await self._cache.get(cache_key)
            if cached:
                return EmbeddingResponse(vector=cached, model=clip.model_info.name)

            vector = await asyncio.get_event_loop().run_in_executor(
                None, lambda: clip.embed_image_from_url(request.content)
            )
            await self._cache.set(cache_key, vector.tolist(), ttl=86400 * 7)

        return EmbeddingResponse(
            vector=vector.tolist(),
            model=clip.model_info.name,
            dimension=len(vector)
        )
```

### 3.4 消息队列 Worker

```python
# app/workers/tagging_worker.py

import asyncio
import json
import logging
import aio_pika
from app.services.tagging_service import TaggingService
from app.infrastructure.mq import get_mq_connection
from app.schemas.tagging import TaggingRequest

logger = logging.getLogger(__name__)

class TaggingWorker:
    """
    消费 Java 侧发来的打标任务
    处理完成后将结果发回队列
    """

    def __init__(self):
        self._service = TaggingService()
        self._max_retries = 3

    async def start(self):
        connection = await get_mq_connection()
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=5)  # 并发消费数

        # 消费打标任务队列
        submit_queue = await channel.declare_queue(
            "ai.tagging.submit",
            durable=True
        )
        # 结果发回队列
        result_exchange = await channel.declare_exchange(
            "ai.exchange",
            aio_pika.ExchangeType.TOPIC,
            durable=True
        )

        async with submit_queue.iterator() as queue_iter:
            async for message in queue_iter:
                async with message.process(requeue=False):
                    await self._handle_message(message, result_exchange)

    async def _handle_message(self, message, result_exchange):
        body = json.loads(message.body.decode())
        picture_id = body.get("pictureId")
        image_url = body.get("imageUrl")
        task_id = body.get("taskId")

        logger.info("Processing tagging task: taskId=%s pictureId=%s", task_id, picture_id)

        try:
            request = TaggingRequest(
                picture_id=picture_id,
                image_url=image_url,
                image_url_hash=body.get("imageUrlHash", ""),
                min_confidence=body.get("minConfidence", 0.65),
                max_tags=body.get("maxTags", 20)
            )
            response = await self._service.tag_image(request)

            # 将结果发回 Java 侧
            result_payload = {
                "taskId": task_id,
                "pictureId": picture_id,
                "success": True,
                "tags": [t.dict() for t in response.tags],
                "provider": response.provider,
                "modelVersion": response.model_version,
            }
            await result_exchange.publish(
                aio_pika.Message(
                    body=json.dumps(result_payload).encode(),
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT
                ),
                routing_key="ai.tagging.result"
            )
            logger.info("Tagging completed: pictureId=%s tags=%d", picture_id, len(response.tags))

        except Exception as e:
            logger.error("Tagging failed: pictureId=%s error=%s", picture_id, e, exc_info=True)
            error_payload = {
                "taskId": task_id,
                "pictureId": picture_id,
                "success": False,
                "errorMessage": str(e)
            }
            await result_exchange.publish(
                aio_pika.Message(body=json.dumps(error_payload).encode()),
                routing_key="ai.tagging.result"
            )
```

### 3.5 FastAPI 路由层

```python
# app/main.py

from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.models.registry import init_registry
from app.config import get_config
from app.api.v1 import tagging, moderation, embedding, assistant
from app.api import health
from app.workers.tagging_worker import TaggingWorker
from app.workers.moderation_worker import ModerationWorker
from app.infrastructure.metrics import setup_metrics
import asyncio
import logging

logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动：初始化模型、启动 Worker
    config = get_config()
    registry = init_registry(config)
    logger.info("Model registry initialized")

    # 后台启动消息队列 Worker
    tagging_worker = TaggingWorker()
    moderation_worker = ModerationWorker()
    tasks = [
        asyncio.create_task(tagging_worker.start()),
        asyncio.create_task(moderation_worker.start()),
    ]

    setup_metrics(app)
    logger.info("AI Platform started")
    yield

    # 关闭：取消所有后台任务
    for task in tasks:
        task.cancel()
    logger.info("AI Platform shutdown")

app = FastAPI(
    title="Cloud Picture AI Platform",
    version="1.0.0",
    lifespan=lifespan
)

app.include_router(tagging.router, prefix="/api/v1/tagging", tags=["Tagging"])
app.include_router(moderation.router, prefix="/api/v1/moderation", tags=["Moderation"])
app.include_router(embedding.router, prefix="/api/v1/embedding", tags=["Embedding"])
app.include_router(assistant.router, prefix="/api/v1/assistant", tags=["Assistant"])
app.include_router(health.router, prefix="/health", tags=["Health"])
```

```python
# app/api/v1/embedding.py

from fastapi import APIRouter, Depends, HTTPException
from app.services.embedding_service import EmbeddingService
from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse
from app.api.deps import verify_api_key  # 内部服务鉴权

router = APIRouter()

@router.post("/text", response_model=EmbeddingResponse)
async def embed_text(
    request: EmbeddingRequest,
    _: None = Depends(verify_api_key)
):
    """文本向量化，用于语义搜索"""
    service = EmbeddingService()
    return await service.embed(request)

@router.post("/image", response_model=EmbeddingResponse)
async def embed_image(
    request: EmbeddingRequest,
    _: None = Depends(verify_api_key)
):
    """图片向量化，用于以图搜图"""
    service = EmbeddingService()
    return await service.embed(request)

@router.get("/health")
async def embedding_health():
    from app.models.registry import get_registry
    registry = get_registry()
    clip_info = registry.get_clip().health_check()
    return {"status": "ok", "model": clip_info}
```

---

## 四、部署架构

### 4.1 Docker Compose（开发环境）

```yaml
# docker-compose.yml

version: '3.9'

services:
  # Java 主业务
  java-app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - AI_PLATFORM_URL=http://ai-platform:8000
      - RABBITMQ_HOST=rabbitmq
      - REDIS_HOST=redis
      - DB_HOST=postgres
    depends_on:
      - ai-platform
      - rabbitmq
      - redis
      - postgres

  # Python AI 中台
  ai-platform:
    build: ./ai-platform
    ports:
      - "8000:8000"
    environment:
      - RABBITMQ_URL=amqp://guest:guest@rabbitmq:5672
      - REDIS_URL=redis://redis:6379
      - CLIP_MODEL_NAME=ViT-L-14
      - MODERATION_PROVIDER=aliyun
      - ALIYUN_AK=${ALIYUN_AK}
      - ALIYUN_SK=${ALIYUN_SK}
    volumes:
      - model-cache:/app/models/cache  # 模型文件持久化
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]     # GPU 支持（可选）

  # 消息队列
  rabbitmq:
    image: rabbitmq:3.13-management
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      - RABBITMQ_DEFAULT_USER=admin
      - RABBITMQ_DEFAULT_PASS=admin123

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  postgres:
    image: pgvector/pgvector:pg16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=cloud_picture
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=123456

volumes:
  model-cache:
```

### 4.2 生产部署拓扑

```
                    ┌─────────────────────────────────────┐
                    │         Kubernetes Cluster           │
                    │                                      │
                    │  ┌──────────────────────────────┐   │
                    │  │     Java 业务 Pods (x3)       │   │
                    │  │  - HPA：CPU > 70% 自动扩容    │   │
                    │  └──────────────┬───────────────┘   │
                    │                 │                    │
                    │  ┌──────────────▼───────────────┐   │
                    │  │     AI Platform Pods          │   │
                    │  │  ┌─────────┐  ┌───────────┐  │   │
                    │  │  │Embedding│  │  Tagging  │  │   │
                    │  │  │Service  │  │  Worker   │  │   │
                    │  │  │(x2 CPU) │  │(x2 GPU)   │  │   │
                    │  │  └─────────┘  └───────────┘  │   │
                    │  │  - GPU节点：打标/审核          │   │
                    │  │  - CPU节点：Embedding API     │   │
                    │  └──────────────────────────────┘   │
                    └─────────────────────────────────────┘
```

---

## 五、完整时间路线图

```
Phase 0：基础设施搭建（Week 1-2）
├── RabbitMQ 部署与 Exchange/Queue 规划
├── Java 侧 AiGateway 骨架（含熔断配置）
├── Python AI 中台项目骨架（FastAPI + Worker）
└── Docker Compose 联调环境

Phase 1：智能打标上线（Week 3-6）
├── CLIP 模型集成与测试
├── TaggingService + TaggingWorker 实现
├── Java 侧结果消费 + 写库
├── 打标置信度调优（目标：用户接受率 > 70%）
└── 灰度：10% 新上传图片触发 AI 打标

Phase 2：内容审核上线（Week 7-10）
├── 接入阿里云内容安全 API
├── ModerationService + ModerationWorker 实现
├── 审核状态机扩展（新增 AUTO_APPROVED 等）
├── 管理端新增 AI 审核统计面板
└── 灰度：公开图片全量启用 AI 初筛

Phase 3：语义搜索上线（Week 11-16）
├── pgvector 扩展安装与索引建立
├── EmbeddingService HTTP API 实现
├── 图片入库时异步生成向量并写入 pgvector
├── Java 侧混合检索（向量 + 关键词 RRF 融合）
├── 以图搜图接口实现
└── A/B 测试：语义搜索 vs 关键词搜索点击率

Phase 4：智能助手上线（Week 17-22）
├── Qwen-VL 模型集成（图片描述生成）
├── 对话式助手 API（意图识别 + Function Call）
├── 前端助手入口组件
└── 用量配额控制（防止滥用）

Phase 5：数据飞轮（Week 23-26）
├── 用户行为埋点完善
├── 标签反馈机制（接受/拒绝）驱动模型微调
├── AI 效果监控大盘
└── 个性化推荐升级（向量化用户兴趣模型）
```

---

## 六、关键风险管控

| 风险 | 应对 |
|------|------|
| AI 中台宕机影响主业务 | 熔断降级：打标失败→人工打标，审核失败→转人工，搜索失败→关键词兜底 |
| GPU 资源成本 | 按需调度：无任务时缩容到 0，弹性伸缩 |
| 模型版本升级影响结果一致性 | 版本号写入每条 AI 记录，升级时旧数据保留，新数据用新模型 |
| 消息队列积压 | 监控队列深度，超阈值告警 + 临时增加 Worker 副本 |
| 向量搜索冷启动效果差 | 混合检索权重可动态调整，初期向量权重低，随数据积累逐步提升 |