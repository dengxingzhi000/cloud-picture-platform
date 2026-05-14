# Cloud Picture Platform 功能演进与 AI 能力建设路线图

## 总体思路

当前平台已具备图片管理、团队协作、审核流、实时协同的核心骨架。下一阶段的核心命题是：**从"存图工具"升级为"智能图片资产平台"**。功能演进和 AI 能力两条线并行推进，AI 能力服务于功能演进，功能演进为 AI 能力提供数据飞轮。

---

## 一、功能演进路线

### Phase 1 — 资产治理闭环（第 1-8 周）

> 目标：让团队能真正"管好"图片资产，而不只是"存图"

**1.1 相册与文件夹体系**

当前所有图片平铺在空间内，缺乏组织结构。

```
需要新增的领域对象：

Album (相册)
  ├── id: UUID
  ├── spaceId: UUID
  ├── name: String
  ├── coverPictureId: UUID
  ├── visibility: Visibility
  └── sortOrder: Integer

AlbumPicture (关联表)
  ├── albumId: UUID
  ├── pictureId: UUID
  └── sortOrder: Integer
```

新增 API：

```
POST   /api/albums                    → 创建相册
GET    /api/albums                    → 列出我的相册
POST   /api/albums/{id}/pictures      → 批量添加图片到相册
GET    /api/albums/{id}/pictures      → 获取相册图片（支持排序）
PATCH  /api/albums/{id}/cover         → 设置封面
DELETE /api/albums/{id}/pictures/{pid}→ 从相册移除
```

**1.2 空间配额与用量治理**

当前 `quotaBytes` 字段存在但无校验逻辑。

```java
// 需要在 upload() 前增加配额检查
// application/space/SpaceQuotaService.java

@Service
public class SpaceQuotaService {
    
    public void assertQuotaAvailable(UUID spaceId, long fileSizeBytes) {
        Space space = spaceRepository.findById(spaceId)
            .orElseThrow(...);
        
        if (space.getQuotaBytes() > 0 
            && space.getUsedBytes() + fileSizeBytes > space.getQuotaBytes()) {
            throw new ApiException(
                ApiErrorCode.QUOTA_EXCEEDED, 
                "空间配额不足，剩余 " + (space.getQuotaBytes() - space.getUsedBytes()) + " bytes"
            );
        }
    }
    
    public SpaceUsageResponse getUsage(UUID spaceId) {
        // 返回已用/总量/文件数/图片数分布
    }
}
```

新增 API：

```
GET  /api/spaces/{id}/usage           → 用量详情
GET  /api/spaces/{id}/usage/trend     → 近30天用量趋势
POST /api/admin/spaces/{id}/quota     → 管理员调整配额
```

**1.3 图片版本管理**

团队编辑场景下，需要追踪图片的历史版本。

```
PictureVersion
  ├── id: UUID
  ├── pictureId: UUID
  ├── version: Integer
  ├── storageKey: String          → 指向该版本的实际文件
  ├── fileSize: Long
  ├── createdByUserId: UUID
  ├── changeNote: String
  └── createdAt: Instant

新增 API：
GET    /api/pictures/{id}/versions         → 版本列表
POST   /api/pictures/{id}/versions         → 上传新版本
GET    /api/pictures/{id}/versions/{v}     → 获取指定版本
POST   /api/pictures/{id}/versions/{v}/restore → 回退到指定版本
```

**1.4 批量操作**

```
POST /api/pictures/batch/delete           → 批量删除
POST /api/pictures/batch/move             → 批量移动到相册/空间
POST /api/pictures/batch/visibility       → 批量修改可见性
POST /api/pictures/batch/tag              → 批量打标签
GET  /api/pictures/batch/export           → 批量导出（ZIP 打包下载）
```

---

### Phase 2 — 协作体验升级（第 9-16 周）

> 目标：让团队协作从"共享存储"升级为"真正协同工作"

**2.1 评论与标注系统**

```
PictureComment
  ├── id: UUID
  ├── pictureId: UUID
  ├── authorId: UUID
  ├── content: String
  ├── parentId: UUID (支持回复)
  ├── x, y: Double (图片坐标，支持定点标注)
  ├── resolved: Boolean
  └── createdAt: Instant

新增 API：
POST   /api/pictures/{id}/comments        → 发表评论/标注
GET    /api/pictures/{id}/comments        → 获取评论列表
PATCH  /api/pictures/{id}/comments/{cid}  → 修改评论
DELETE /api/pictures/{id}/comments/{cid}  → 删除评论
POST   /api/pictures/{id}/comments/{cid}/resolve → 标记已解决
```

实时推送评论事件到 STOMP topic，与现有协同编辑 topic 复用。

**2.2 图片水印与导出规格**

```
WatermarkConfig（团队级配置）
  ├── teamId: UUID
  ├── enabled: Boolean
  ├── type: ENUM(TEXT, IMAGE)
  ├── text: String
  ├── opacity: Float
  ├── position: ENUM(CENTER, BOTTOM_RIGHT, ...)
  └── logoStorageKey: String

ExportPreset（导出规格预设）
  ├── name: String (如"社交媒体 1:1")
  ├── width, height: Integer
  ├── format: ENUM(JPEG, PNG, WEBP)
  ├── quality: Integer
  └── watermarkEnabled: Boolean

新增 API：
POST /api/pictures/{id}/export            → 按规格导出（异步任务）
GET  /api/export-tasks/{taskId}           → 查询导出任务状态
GET  /api/teams/{id}/watermark            → 获取水印配置
PUT  /api/teams/{id}/watermark            → 更新水印配置
```

**2.3 活动流与团队动态**

```
TeamActivity
  ├── id: UUID
  ├── teamId: UUID
  ├── actorId: UUID
  ├── type: ENUM(PICTURE_UPLOADED, COMMENT_ADDED, MEMBER_JOINED, ...)
  ├── targetId: UUID (图片/评论/成员的 ID)
  ├── payload: JSONB
  └── createdAt: Instant

新增 API：
GET /api/teams/{id}/activities            → 团队动态流（时间线）
GET /api/teams/{id}/activities/digest     → 每日摘要
```

---

### Phase 3 — 开放能力（第 17-24 周）

> 目标：打通外部系统，让平台成为内容供应链的一部分

**3.1 Webhook**

```
WebhookEndpoint
  ├── id: UUID
  ├── teamId / userId: UUID
  ├── url: String
  ├── secret: String (HMAC 签名密钥)
  ├── events: String[] (订阅的事件类型)
  └── active: Boolean

事件类型：
  picture.uploaded / picture.approved / picture.rejected
  comment.added / member.invited / member.joined

新增 API：
POST   /api/webhooks                      → 注册 Webhook
GET    /api/webhooks                      → 列出
DELETE /api/webhooks/{id}                 → 删除
POST   /api/webhooks/{id}/test            → 发送测试事件
GET    /api/webhooks/{id}/deliveries      → 投递历史
```

**3.2 Open API / Developer Portal**

```
ApiKey
  ├── id: UUID
  ├── userId: UUID
  ├── name: String
  ├── keyHash: String (仅存哈希)
  ├── scopes: String[] (picture:read / picture:write / team:read)
  ├── rateLimit: Integer (QPS)
  └── expiresAt: Instant

实现路径：
1. 新增 ApiKeyAuthFilter，支持 X-API-Key 头认证
2. 为 API Key 添加细粒度 scope 控制
3. 接入 Redis 实现令牌桶限流
4. 生成 OpenAPI 文档（springdoc-openapi）
```

---

## 二、AI 能力建设路线

### AI 架构总体设计

```
┌─────────────────────────────────────────────────────┐
│                   AI Gateway Layer                   │
│         (统一 AI 服务调用、降级、计费、审计)           │
└──────────┬──────────┬──────────┬────────────────────┘
           │          │          │
    ┌──────▼──┐ ┌─────▼──┐ ┌───▼──────┐
    │ 标签服务 │ │ 审核服务│ │ 搜索服务  │
    └──────┬──┘ └─────┬──┘ └───┬──────┘
           │          │        │
    ┌──────▼──────────▼────────▼──────┐
    │        AI Provider Adapters      │
    │  (阿里云视觉 / OpenAI / 本地模型) │
    └─────────────────────────────────┘
```

**核心设计原则：**
- AI 调用全部异步化，不阻塞上传主流程
- Provider 适配器模式，随时切换/降级
- 所有 AI 结果可追溯，标注 provider + version + confidence

---

### AI Phase 1 — 智能标签（第 1-4 周）

> 当前 `PictureTag` 已预留 `provider`、`confidenceScore`、`isAutoGenerated` 字段，基础设施已就绪

**架构设计：**

```java
// domain/ai/ImageTaggingService.java（领域接口）
public interface ImageTaggingService {
    CompletableFuture<List<TagSuggestion>> analyzeImage(String imageUrl);
    String providerName();
    String modelVersion();
}

// infrastructure/ai/AliyunImageTaggingAdapter.java
@Service
@ConditionalOnProperty("app.ai.provider", havingValue = "aliyun")
public class AliyunImageTaggingAdapter implements ImageTaggingService {
    // 调用阿里云视觉智能 API
}

// infrastructure/ai/OpenAiImageTaggingAdapter.java  
@Service
@ConditionalOnProperty("app.ai.provider", havingValue = "openai")
public class OpenAiImageTaggingAdapter implements ImageTaggingService {
    // 调用 GPT-4o vision API 分析图片内容
}
```

**触发流程：**

```
图片上传完成
    ↓
ApplicationEvent: PictureUploadedEvent
    ↓ (@TransactionalEventListener AFTER_COMMIT)
ImageTaggingScheduler.onPictureUploaded()
    ↓ (异步线程池)
ImageTaggingService.analyzeImage(imageUrl)
    ↓
PictureTagService.addAutoGeneratedTags(pictureId, suggestions)
    ↓
SearchIndexService.enqueuePicture(pictureId)  → 重建搜索索引
    ↓
NotificationPublisher → 通知用户"已自动标签"（可配置关闭）
```

**新增配置：**

```yaml
app:
  ai:
    provider: aliyun          # aliyun / openai / disabled
    tagging:
      enabled: true
      min-confidence: 0.7     # 低于此置信度的标签不采用
      max-tags-per-image: 20
      async-timeout-seconds: 30
    aliyun:
      access-key-id: ${ALIYUN_AK}
      access-key-secret: ${ALIYUN_SK}
      region: cn-shanghai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
```

**新增 API：**

```
POST /api/pictures/{id}/ai/retag          → 手动触发重新打标
GET  /api/pictures/{id}/ai/tag-history    → 查看 AI 标签历史
POST /api/pictures/{id}/ai/tags/{tagId}/feedback → 标签反馈（对/错）
```

---

### AI Phase 2 — 智能审核（第 5-8 周）

> 减少人工审核压力，AI 做初筛，人工做最终裁决

**审核流程重设计：**

```
当前流程：
  上传 → PENDING → 人工审核 → APPROVED/REJECTED

新流程：
  上传 → AI_REVIEWING → AI初筛
    ├── 高置信度安全   → AUTO_APPROVED（可配置跳过人工）
    ├── 高置信度违规   → AUTO_REJECTED（附带原因）
    └── 置信度不足     → PENDING（转人工）
```

```java
// 新增审核状态
public enum ReviewStatus {
    PENDING,
    AI_REVIEWING,    // 新增：AI 审核中
    AI_REVIEWED,     // 新增：AI 已审核，等待人工复核
    APPROVED,
    REJECTED,
    AUTO_APPROVED,   // 新增：AI 自动通过
    AUTO_REJECTED    // 新增：AI 自动拒绝
}

// domain/ai/ContentModerationService.java
public interface ContentModerationService {
    CompletableFuture<ModerationResult> moderate(String imageUrl);
    
    record ModerationResult(
        boolean safe,
        float confidence,
        List<String> violationCategories,  // 色情/暴力/广告等
        String provider,
        String rawResponse
    ) {}
}
```

**新增数据表：**

```sql
CREATE TABLE ai_moderation_record (
    id              UUID PRIMARY KEY,
    picture_id      UUID NOT NULL,
    provider        VARCHAR(50),
    model_version   VARCHAR(50),
    is_safe         BOOLEAN,
    confidence      DOUBLE PRECISION,
    violation_cats  TEXT[],
    raw_response    JSONB,
    processing_ms   INTEGER,
    created_at      TIMESTAMPTZ NOT NULL
);
```

**管理端新增 API：**

```
GET  /api/admin/ai/moderation/stats       → AI 审核准确率统计
GET  /api/admin/ai/moderation/config      → 获取自动审核配置
PUT  /api/admin/ai/moderation/config      → 调整置信度阈值
GET  /api/admin/ai/moderation/errors      → 误判记录（人工纠正的）
```

---

### AI Phase 3 — 语义搜索（第 9-14 周）

> 从关键词匹配升级为理解语义，实现"找到想找的图"

**方案一：向量搜索（推荐）**

```
图片入库时：
  图片 → CLIP 模型 → 512维向量 → 存入 pgvector

搜索时：
  文字描述 → CLIP 文本编码 → 512维向量
                ↓
  pgvector 余弦相似度检索 → Top-K 图片
```

```sql
-- 依赖 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE picture_search_document 
  ADD COLUMN image_embedding vector(512);

CREATE INDEX idx_image_embedding 
  ON picture_search_document 
  USING ivfflat (image_embedding vector_cosine_ops)
  WITH (lists = 100);
```

```java
// domain/ai/EmbeddingService.java
public interface EmbeddingService {
    CompletableFuture<float[]> embedImage(String imageUrl);
    CompletableFuture<float[]> embedText(String text);
}

// 搜索时混合检索
public class HybridSearchService {
    
    public PageResponse<PictureSummary> search(String query, int page, int size) {
        // 1. 向量语义搜索
        float[] queryEmbedding = embeddingService.embedText(query).join();
        List<UUID> semanticResults = vectorSearch(queryEmbedding, page * size + size);
        
        // 2. 关键词精确搜索（原有逻辑）
        List<UUID> keywordResults = keywordSearch(query, page * size + size);
        
        // 3. RRF 融合排序 (Reciprocal Rank Fusion)
        List<UUID> merged = reciprocalRankFusion(semanticResults, keywordResults);
        
        return buildPageResponse(merged, page, size);
    }
}
```

**新增 API：**

```
GET /api/pictures/search/semantic         → 语义搜索入口
GET /api/pictures/{id}/similar            → 视觉相似图片（已有pHash，升级为向量）
POST /api/search/image                    → 以图搜图（上传图片查找相似）
```

---

### AI Phase 4 — 智能助手（第 15-20 周）

> 让 AI 真正融入工作流，而不只是后台服务

**4.1 图片描述生成**

```
POST /api/pictures/{id}/ai/describe       → 生成图片文字描述
POST /api/pictures/{id}/ai/alt-text       → 生成无障碍 Alt 文本
POST /api/pictures/{id}/ai/seo-title      → 生成 SEO 优化标题

// 应用场景：
// - 帮助用户快速填写图片元数据
// - 为搜索引擎优化提供内容
// - 无障碍合规
```

**4.2 智能相册整理**

```
POST /api/albums/ai/organize              → AI 自动将图片分类到相册
POST /api/spaces/{id}/ai/deduplicate      → AI 检测视觉重复图片
GET  /api/spaces/{id}/ai/insights         → 空间资产洞察报告

// 洞察报告示例：
{
  "totalPictures": 1234,
  "topCategories": ["产品图", "人物", "风景"],
  "duplicateGroups": 23,        // 检测到的重复组
  "lowQualityCount": 45,        // 模糊/过曝/欠曝
  "unusedPictures": 89,         // 90天未访问
  "storageWaste": "2.3GB"       // 重复+低质量占用
}
```

**4.3 对话式图片助手（RAG 架构）**

```
用户：找一张上个月团队活动的集体照
AI：我找到了 3 张符合条件的图片... [图片列表]

用户：帮我把这周上传的产品图都加上"2024Q4"标签
AI：正在为 12 张图片添加标签... 完成

// 实现：
// 1. 用户输入 → 意图识别（分类：搜索/操作/查询）
// 2. 根据意图调用对应 API
// 3. 结果格式化返回
// 4. 多轮对话通过 sessionId 维护上下文
```

```java
// interfaces/ai/AiAssistantController.java
@PostMapping("/api/ai/chat")
public ApiResponse<AiChatResponse> chat(
    @RequestBody AiChatRequest request,
    @AuthenticationPrincipal AppUserPrincipal principal
) {
    // request.sessionId  → 会话ID（多轮对话）
    // request.message    → 用户输入
    // request.contextPictureIds → 当前选中的图片（可选）
}
```

---

### AI Phase 5 — 数据飞轮（第 21-26 周）

> AI 帮助平台越用越智能

**5.1 用户行为收集**

```sql
CREATE TABLE user_behavior_event (
    id              UUID PRIMARY KEY,
    user_id         UUID,
    event_type      VARCHAR(50),  -- view/download/share/search/tag_accept/tag_reject
    picture_id      UUID,
    session_id      VARCHAR(64),
    search_query    TEXT,
    duration_ms     INTEGER,
    metadata        JSONB,
    created_at      TIMESTAMPTZ
);
```

**5.2 个性化推荐升级**

```
当前：基于标签重叠的简单推荐（已实现）

升级路径：
Phase 5a → 基于协同过滤（用户行为矩阵分解）
Phase 5b → 基于向量相似度（用户兴趣向量 + 图片向量）
Phase 5c → 实时个性化（Redis 实时更新用户兴趣模型）
```

**5.3 AI 模型效果监控**

```
GET /api/admin/ai/metrics
响应：
{
  "tagging": {
    "accuracy": 0.87,            // 用户接受率
    "avgConfidence": 0.82,
    "avgTagsPerImage": 8.3,
    "p99LatencyMs": 2300
  },
  "moderation": {
    "autoApprovalRate": 0.73,
    "falsePositiveRate": 0.02,   // 人工纠正率
    "avgProcessingMs": 1200
  },
  "search": {
    "semanticClickthrough": 0.61,
    "keywordClickthrough": 0.45  // 语义搜索效果更好
  }
}
```

---

## 三、完整时间路线图

```
2024 Q4 (Week 1-12)
├── Week 1-4   相册体系 + 配额治理 + AI 自动标签
├── Week 5-8   版本管理 + 批量操作 + AI 内容审核
└── Week 9-12  评论标注 + 向量搜索基础

2025 Q1 (Week 13-24)
├── Week 13-16 水印导出 + 团队活动流 + 语义搜索上线
├── Week 17-20 Webhook + AI 图片描述 + 智能相册整理
└── Week 21-24 Open API + 对话助手 + 行为数据收集

2025 Q2 (Week 25-32)
├── Week 25-28 数据飞轮 + 个性化推荐升级
└── Week 29-32 AI 效果监控 + 模型迭代
```

---

## 四、AI 能力选型建议

| 能力 | 推荐方案 | 备选 | 理由 |
|------|---------|------|------|
| 图像标签 | 阿里云视觉智能 | Google Vision API | 国内合规，延迟低 |
| 内容审核 | 阿里云内容安全 | 腾讯云天御 | 中文语境准确率高 |
| 图文向量 | CLIP (自部署) | OpenAI Embeddings | 控制数据安全 |
| 语义理解 | GPT-4o / Qwen-VL | Claude | 多模态能力强 |
| 向量存储 | pgvector | Milvus | 复用现有 PG，运维简单 |
| 对话助手 | 阿里云百炼 (qwen) | OpenAI | 国内合规，Function Call 完善 |

**选型核心原则：**
- 数据不出境：图片内容不传境外，优先使用国内云服务
- 降级优先：AI 服务不可用时，平台核心功能必须正常运行
- 成本可控：按量计费，设置每日调用上限和告警

---

## 五、关键风险与应对

| 风险 | 概率 | 影响 | 应对策略 |
|------|------|------|---------|
| AI 标签准确率不达预期 | 中 | 中 | 灰度发布，收集反馈，设置置信度阈值 |
| 向量搜索首次冷启动效果差 | 高 | 中 | 混合检索兜底，逐步提升向量权重 |
| AI 调用成本超预算 | 中 | 高 | 每日限额 + 降级开关 + 按团队计费 |
| 内容审核误判引发用户投诉 | 中 | 高 | 保留人工复核入口，误判可申诉 |
| pgvector 性能不满足 | 低 | 高 | 提前压测，超过千万量级切 Milvus |
---

## 六、前端演进路线

> 基于 React 19 + TypeScript + Vite 技术栈，与后端功能演进同步推进

### Phase 1 — 资产治理（第 1-8 周）

```
功能点                             对应后端         技术要点
──────────────────────────────────────────────────────────
相册管理页面（创建/编辑/排序）       1.1             react-router 嵌套路由，拖拽排序
空间用量仪表盘                     1.2             recharts / @nivo 用量趋势图
图片版本时间线                     1.3             横向时间轴 GSAP 动画
批量选择工具栏                     1.4             React Context 管理选中状态，Shift 多选
```

**关键组件：**

```
src/react-app/pages/
├── album/
│   ├── AlbumListPage.tsx
│   ├── AlbumDetailPage.tsx
│   └── AlbumSelectorDialog.tsx      → shadcn Dialog 弹窗选相册
src/react-app/components/
├── space/
│   └── UsageDashboard.tsx           → 用量仪表盘
└── picture/
    ├── VersionTimeline.tsx          → 版本时间线
    └── BatchActionBar.tsx           → 批量操作浮动栏
```

### Phase 2 — 协作体验升级（第 9-16 周）

```
功能点                             对应后端         技术要点
──────────────────────────────────────────────────────────
图片评论区（含坐标标注）           2.1             Canvas 叠加标注，MouseEvent 坐标映射
水印预览                          2.2             实时水印效果预览（Canvas 合成）
导出规格选择器                     2.2             shadcn Select + 自定义预设
团队动态流                         2.3             IntersectionObserver 无限滚动 + STOMP 实时
```

**关键组件：**

```
src/react-app/components/
├── comment/
│   ├── CommentThread.tsx            → 评论线程
│   └── ImageAnnotator.tsx           → 图片坐标标注（Canvas）
├── export/
│   ├── ExportPresetSelector.tsx
│   └── ExportTaskStatus.tsx         → 轮询/SSE 进度跟踪
└── activity/
    └── ActivityFeed.tsx             → 动态流
```

### Phase 3 — 开放能力（第 17-24 周）

```
功能点                             对应后端         技术要点
──────────────────────────────────────────────────────────
Webhook 配置面板                   3.1             shadcn Switch + JSON 编辑器
API Key 管理页                     3.2            密钥创建/吊销/复制
开发者文档门户                     3.2             Swagger UI iframe 嵌入
AI 标签反馈入口                   AI Phase 1-2     Thumbs up/down 交互，乐观更新
```

### 前端通用基础设施

```
阶段      任务                             技术方案
─────────────────────────────────────────────────────
Phase 1   无限滚动列表                      IntersectionObserver + react-router search params
Phase 1   STOMP 实时消息统一管理            @stomp/stompjs + sockjs-client
Phase 2   图片标注 Canvas 工具集            Canvas 2D API + useRef
Phase 2   批量操作状态管理                  React Context + useReducer 乐观更新
Phase 3   OpenAPI 客户端自动生成            openapi-typescript（从 Swagger 产出的 TS 类型）
Phase 3   国际化 i18n                       react-i18next（扩展 zh-CN/en）
```

---

## 七、基础设施与 DevOps 演进

### 基础设施路线图

```
Phase 1（Week 1-8）
├── CI/CD 流水线：Maven 构建 → 单元测试 → Docker 镜像 → 部署
├── SonarQube 代码质量门禁
├── Prometheus + Grafana 监控大盘
└── ELK / Loki 日志聚合

Phase 2（Week 9-16）
├── RabbitMQ 监控（队列深度、消费延迟）
├── AI 中台 GPU 资源监控
├── 数据库连接池 + 慢查询监控
└── 端到端 APM（SkyWalking / OpenTelemetry）

Phase 3（Week 17-24）
├── Kubernetes 部署（开发环境先行）
├── HPA 弹性伸缩策略
├── 蓝绿发布 / 金丝雀发布
└── 多区域容灾
```

### 性能目标

```
指标             当前基线      目标        测量方式
─────────────────────────────────────────────────────
图片上传 P99      1.2s         800ms      K6 压测
图片搜索 P99      300ms        200ms      K6 压测
AI 打标 P99       无           3s         AI 中台日志
AI 审核 P99       无           2s         AI 中台日志
系统可用性        99.5%        99.9%       Prometheus
```

---

## 八、成功度量指标

### 业务指标

```
指标                            目标             数据来源
─────────────────────────────────────────────────────────
团队月活跃用户 (MAU)             +30%             用户行为表
人均存储图片数                   +50%             空间用量
图片二次使用率（下载/引用）      +40%             用户行为表
批量操作使用率                   30% 活跃团队     功能埋点
Webhook / API Key 激活率         20% 企业团队     数据库
```

### AI 效果指标

```
AI 能力      核心指标                目标          采集方式
──────────────────────────────────────────────────────────
智能标签     用户标签接受率           > 75%        标签反馈事件
             自动标签覆盖度           > 60% 图片    打标记录
智能审核     自动通过率               > 70%        审核记录
             误判率（人工纠正比例）    < 5%         AI审核 + 人工复核对比
语义搜索     语义搜索点击率           > 60%        搜索事件埋点
             搜索满意度（用户反馈）    > 4.0/5.0    问卷/隐式反馈
智能助手     任务完成率               > 80%        对话结束后的确认
             用户留存率               > 30% 周留存  使用频率统计
```

### 技术指标

```
指标                        目标            监控工具
─────────────────────────────────────────────────────
构建流水线通过率              > 98%          CI Dashboard
AI 中台可用性                 > 99.5%        Prometheus
消息队列积压恢复时间          < 5 分钟      告警 + 自动扩容
数据库慢查询（>200ms）        < 0.1%         pg_stat_statements
前端页面加载时间（LCP）       < 2.5 秒       Lighthouse CI
```
