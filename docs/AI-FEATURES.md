# AI-FEATURES.md

> AI 能力增强需求文档 — Cloud Picture Platform (P5 AI 模块)
> 技术路线：**Spring AI + Java**（在现有 Spring Boot 项目中直接集成，不引入 Python 服务）。

---

## 1. 可用 API 资源清单（2026-04 核实）

| 服务 | 用途 | 免费额度 | 超出单价 | 备注 |
|---|---|---|---|---|
| **DeepSeek API** (`deepseek-chat`) | 文本推理、审核决策、标签推荐、搜索理解 | 注册赠送额度（见平台） | ~¥1/百万 token | 已有 API Key，优先使用 |
| **DeepSeek `deepseek-chat`** (兜底) | 用文字描述代替视觉调用 | 同上 | 同上 | 降级策略，详见第 4 节 |
---

## 2. 技术路线决策：Spring AI vs Python

### 结论：使用 **Spring AI 1.1.4**，在现有 Java 项目中直接集成

| 维度 | Spring AI (Java) | Python 微服务 |
|---|---|---|
| 学习目标 | 学习企业级 AI 集成，和现有项目同栈 | 学习 Python 生态，需维护两套服务 |
| 运维成本 | 零——打进同一个 JAR | 独立容器、独立 CI/CD、独立健康检查 |
| DeepSeek 接入 | OpenAI 兼容模式，5 行 yml 即可 | 同样简单，但需额外网络调用 |

### Spring AI 接入 DeepSeek 的核心配置

DeepSeek 提供 OpenAI 兼容 API，可直接基于 `spring-ai-openai-spring-boot-starter` 接入 DeepSeek 模型，只需把 `base-url` 改为 DeepSeek 端点、填入 API Key 即可，无需修改业务代码。

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
# application.yml — DeepSeek 文本模型（审核决策 / 标签推荐 / 搜索理解）
spring:
  ai:
    openai:
      api-key: ${app.ai.deepseek.api-key}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
```

```yaml
# application.yml — Qwen-VL 视觉模型（图片描述）
# 使用独立 ChatModel Bean，和 DeepSeek Bean 共存
app:
  ai:
    qwen-vl:
      api-key: ${app.ai.qwen.api-key}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model: qwen-vl-plus
```

---

## 3. 功能需求清单（按优先级）

### 3.1 P5-F1 — 图片 AI 描述生成（视觉理解）

**目标**：图片上传完成后，异步调用生成一段自然语言描述，存入 `PictureAsset.aiDescription`。

**触发时机**：`PictureService.upload()` 完成后，发送 Kafka 消息 `picture.uploaded`，由 `AiCaptionConsumer` 消费。

**Prompt 模板**（存放于 `resources/prompts/caption.txt`）：

```
请用中文描述这张图片的内容，包括：主体对象、场景环境、色彩风格、可能的用途。
要求：100 字以内，客观描述，不要主观评价。
输出格式：纯文本，不要 Markdown。
```

**输入**：图片 URL（从 MinIO/COS 生成临时访问链接，有效期 10 分钟）或 base64（图片 < 1 MB 时）。

**输出**：写入 `PictureAsset.aiDescription`，同时触发 `AiTagConsumer` 进行自动打标。

**新增 Flyway 迁移**：`V10__add_ai_description_to_picture_asset.sql`
```sql
ALTER TABLE picture_asset ADD COLUMN ai_description TEXT;
ALTER TABLE picture_asset ADD COLUMN ai_processed_at TIMESTAMPTZ;
```

---

### 3.2 P5-F2 — AI 自动打标（标签推荐）

**目标**：根据图片描述文字，调用 DeepSeek 推荐 3~5 个最合适的已有标签，自动关联到 `PictureTag`，置信度来自模型输出。

**调用模型**：`deepseek-chat`（DeepSeek API，文本推理）

**触发时机**：`P5-F1` 描述生成完成后，串行触发。

**Prompt 模板**（`resources/prompts/auto_tag.txt`）：

```
你是一个图片标签推荐系统。

【图片描述】
{description}

【现有标签库】
{tagList}

请从现有标签库中选出最匹配的 3~5 个标签。
如果现有标签均不匹配，可以在末尾建议 1~2 个新标签名称（用 [NEW] 前缀标注）。

严格按照以下 JSON 格式返回，不要输出任何其他内容：
{
  "matched": [
    {"tagName": "风景", "confidence": 0.95},
    {"tagName": "建筑", "confidence": 0.82}
  ],
  "suggested": [
    {"tagName": "城市夜景", "confidence": 0.70}
  ]
}
```

**输出处理**：
- `matched` 中的标签直接关联，写入 `PictureTag`（`confidence` 来自模型）。
- `suggested` 中的标签暂存，管理员在后台一键批量创建并关联。
- JSON 解析失败时，`decision` 默认为空列表，记录原始响应到日志，不抛异常。

---

### 3.3 P5-F3 — AI 内容审核（三阶段流水线）

**目标**：替代纯人工初审，高置信度自动通过/拒绝，低置信度推入人工队列。

#### 阶段 1 — 图像安全检测

#### 阶段 2 — 规则引擎（Java 代码，零成本）

```
if (阶段1.suggestion == "block") → 直接 REJECTED，跳过阶段 3
if (阶段1.suggestion == "pass" && rate < 20) → 直接 APPROVED，跳过阶段 3
else → 进入阶段 3
```

阈值通过 `@ConfigurationProperties("app.ai.moderation")` 配置，不硬编码。

#### 阶段 3 — DeepSeek 综合判断

**调用模型**：`deepseek-chat`

**Prompt 模板**（`resources/prompts/moderation.txt`）：

```
你是一个图片内容审核系统，请根据以下信息做出审核决策。

【图像安全检测结果】
- 检测建议: {suggestion}
- 主要标签: {label}
- 风险评分: {rate}/100

【图像描述】
{aiDescription}

【审核策略】
- 包含色情、暴力、血腥内容 → 拒绝
- 包含明显广告、二维码 → 拒绝（公开图库）
- 描述模糊或无法判断 → 人工复核
- 其余情况 → 通过

严格按照以下 JSON 格式返回，不要输出任何其他内容：
{
  "decision": "APPROVED" | "REJECTED" | "MANUAL_REVIEW",
  "reason": "简短中文说明，不超过 50 字",
  "confidence": 0.0~1.0
}
```

**输出处理**：
- `decision` 写入 `ModerationRecord.aiDecision`，`reason` 写入 `ModerationRecord.aiReason`。
- `confidence < 0.75` 时强制覆盖为 `MANUAL_REVIEW`，无论模型输出什么。
- JSON 解析失败时，`decision` 默认 `MANUAL_REVIEW`，原始响应记录到 `ModerationRecord.rawResponse`。
- 最终 `PictureAsset.reviewStatus` 由人工确认后更新，AI 结果仅供参考，不直接生效（学习阶段保守策略）。

**新增 Flyway 迁移**：`V11__add_ai_fields_to_moderation_record.sql`
```sql
ALTER TABLE moderation_record
    ADD COLUMN ai_decision   VARCHAR(20),
    ADD COLUMN ai_reason     VARCHAR(200),
    ADD COLUMN ai_confidence NUMERIC(4,3),
    ADD COLUMN raw_response  TEXT;
```

---

### 3.4 P5-F4 — 自然语言搜索理解

**目标**：用户输入模糊的自然语言查询，DeepSeek 解析为结构化搜索条件，交给现有 `SearchIndexService` 执行。

**调用模型**：`deepseek-chat`

**触发时机**：`GET /api/v1/pictures/search?q={naturalQuery}` 检测到查询词超过 8 个字符且含有形容词/场景词时，启用 AI 解析；否则走原有关键词匹配。

**Prompt 模板**（`resources/prompts/search_parse.txt`）：

```
你是一个图片搜索查询解析器。

用户输入："{query}"

请将用户意图解析为结构化搜索条件。

严格按照以下 JSON 格式返回，不确定的字段填 null，不要输出任何其他内容：
{
  "keywords": ["关键词1", "关键词2"],
  "tags": ["标签名"],
  "colorHint": "蓝色" | null,
  "orientation": "LANDSCAPE" | "PORTRAIT" | "SQUARE" | null,
  "mood": "明亮" | "暗沉" | null
}
```

**输出处理**：将解析结果转为 `PictureSearchRequest`，复用现有 `SearchIndexService.search()` 方法，不修改搜索核心逻辑。

---

### 3.5 P5-F5 — 管理员 AI 运营周报（定时任务）

**目标**：每周一 08:00 自动生成图库运营摘要，以邮件或日志形式输出给管理员。

**调用模型**：`deepseek-chat`

**触发方式**：`@Scheduled(cron = "0 0 8 * * MON")` + 独立 `WeeklyReportTask`。

**数据来源**：查询过去 7 天的统计数据（SQL 聚合），拼入 prompt。

**Prompt 模板**（`resources/prompts/weekly_report.txt`）：

```
你是一个图片平台的运营分析助手。请根据以下数据生成一份简洁的中文周报摘要（200 字以内）：

本周数据（{startDate} ~ {endDate}）：
- 新上传图片：{uploadCount} 张
- 审核通过：{approvedCount} 张 / 拒绝：{rejectedCount} 张 / 人工复核：{reviewCount} 张
- 新增用户：{newUserCount} 人
- 最热标签 Top5：{topTags}
- 违规类型分布：{violationTypes}

请重点指出本周的异常趋势和建议关注点。
```

---

## 4. 降级策略（免费额度耗尽时）

| 功能 | 主路径 | 降级路径 | 降级条件 |
|---|---|---|---|
| 自动打标 | DeepSeek（基于描述） | 跳过打标，保留人工打标 | 描述为 null 或 DeepSeek 错误 |
| 内容审核阶段 3 | DeepSeek 推理 | 直接输出 `MANUAL_REVIEW` | DeepSeek 错误或超时 |
| 自然语言搜索 | DeepSeek 解析 | 回退为原有关键词匹配 | 解析耗时 > 2 s 或错误 |
| 周报 | DeepSeek 生成 | 输出原始数据表格，不生成文案 | DeepSeek 错误 |

所有降级均通过 Resilience4j Circuit Breaker 自动触发，无需人工干预。

---

## 5. 新增代码结构

```
application/
  ai/
    AiCaptionService.java          ← 图片描述生成
    AiTagService.java              ← 自动打标（调用 DeepSeek）
    AiModerationService.java       ← 内容审核三阶段编排
    AiSearchParserService.java     ← 自然语言搜索解析
    AiReportService.java           ← 周报生成

infrastructure/
  ai/
    DeepSeekClient.java            ← DeepSeek ChatModel Bean（Spring AI OpenAI 兼容）
    PromptLoader.java              ← 从 resources/prompts/*.txt 加载模板

config/
  AiProperties.java                ← @ConfigurationProperties("app.ai")
  AiBeanConfig.java                ← 注册两个独立 ChatModel Bean

resources/
  prompts/
    caption.txt
    auto_tag.txt
    moderation.txt
    search_parse.txt
    weekly_report.txt
```

---

## 6. application.yml 配置段

```yaml
app:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}          # 环境变量注入，禁止硬编码
      base-url: https://api.deepseek.com
      model: deepseek-chat
      timeout-seconds: 15
    qwen-vl:
      api-key: ${QWEN_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model: qwen-vl-plus
      timeout-seconds: 20
    aliyun-green:
      access-key-id: ${ALIYUN_AK_ID}
      access-key-secret: ${ALIYUN_AK_SECRET}
      region-id: cn-shanghai
    moderation:
      auto-approve-threshold: 20            # rate < 20 且 pass → 自动通过
      auto-reject-threshold: 80             # rate > 80 且 block → 直接拒绝
      confidence-min: 0.75                  # 低于此置信度强制 MANUAL_REVIEW
    enabled:
      caption: true                         # 可独立开关每个 AI 功能
      auto-tag: true
      moderation: true
      search-parse: true
      weekly-report: true
```

---

## 7. AI 编码规则（补充到 CLAUDE.md 第 17 节）

```markdown
## 17. AI 分析模块规则（P5）

### 通用
- 所有 AI API Key 通过环境变量注入，禁止出现在任何源文件和日志中。
- Prompt 模板统一存放在 `resources/prompts/` 目录，Java 代码中禁止拼接硬编码 prompt 字符串。
- 所有 AI 调用必须设置超时：caption ≤ 20 s，moderation ≤ 15 s，其余 ≤ 10 s。
- 任意 AI 调用失败时，只记录 warn 日志，不抛异常，不中断主流程，执行对应降级策略。
- DeepSeek 返回内容必须做 JSON 格式校验；解析失败时使用默认值并记录原始响应。
- 禁止在 domain 层调用任何 AI 相关类。

### 三阶段审核特有规则
- 调用顺序严格串行：Detection → （规则引擎）→ DeepSeek Moderation。
- DeepSeek Moderation 的 prompt 必须包含阶段 1 的检测结果，禁止跳步调用。
- `AiModerationResult.decision` 只允许三个值：APPROVED / REJECTED / MANUAL_REVIEW。
- confidence < 0.75 时，decision 强制覆盖为 MANUAL_REVIEW，无论模型输出什么。
- AI 审核结果写入 ModerationRecord，最终 ReviewStatus 由人工确认，AI 结果不直接修改 PictureAsset。

### 成本控制规则
- 开启 Resilience4j CircuitBreaker 包裹所有外部 AI 调用。
- 搜索解析功能仅在查询词 > 8 字符时触发，避免短关键词也消耗额度。
- 周报任务在 app.ai.enabled.weekly-report=false 时完全跳过，不发送任何请求。
```

---

## 8. 学习路线建议

按以下顺序实现，每步都有完整的可验证产出：

```
Step 1  集成 Spring AI + DeepSeek
        → 实现 AiTagService，能根据文字描述推荐标签
        → 验证：POST /api/v1/ai/tag-suggest?desc=xxx 返回 JSON

Step 2 
        → 实现 AiCaptionService，上传一张图能返回描述文字
        → 验证：上传图片后 picture_asset.ai_description 字段有值

Step 3  串联 Step1 + Step2
        → 上传图片 → 自动生成描述 → 自动打标，端到端跑通

Step 4  
        → 实现 AliyunContentSafetyClient，能对图片返回 suggestion
        → 验证：传一张测试图，返回正确的 pass/review/block

Step 5  实现三阶段审核流水线
        → AiModerationService 串联 Step4 + 规则引擎 + DeepSeek
        → 验证：moderation_record 表有完整的 ai_decision + ai_reason

Step 6  自然语言搜索
        → 在搜索接口前加 DeepSeek 解析层，不修改搜索核心
        → 验证：搜索"适合做封面的蓝色风景"能返回相关图片

Step 7  管理后台周报
        → WeeklyReportTask 定时生成文案，输出到日志验证内容
```

---

*文档版本：2026-04 | 适用项目：cloud-picture-platform P5 阶段*
