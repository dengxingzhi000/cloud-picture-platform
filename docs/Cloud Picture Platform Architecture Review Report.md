# Cloud Picture Platform 架构评审报告

## 执行摘要

基于对全量代码的审阅，项目整体架构骨架清晰，DDD 分层到位，但在**领域边界、并发安全、可观测性、测试覆盖**四个维度存在系统性欠缺。以下评审从三个核心视角展开，并给出带时间节点的演进路线。

---

## 一、现状诊断

### 1.1 架构优势

- 分层清晰：`domain / application / infrastructure / interfaces` 四层职责基本明确
- 存储抽象到位：`StorageService` 接口屏蔽了 Local/COS 差异
- 缓存降级设计：`FallbackCacheManager` 实现 Redis → Caffeine 降级，生产友好
- WebSocket 鉴权完整：`WebSocketAuthChannelInterceptor` 在 STOMP 握手层完成 JWT 验证
- 文件去重机制：SHA-256 + pHash 双层去重，`REQUIRES_NEW` 处理并发冲突

### 1.2 系统性问题清单

| 风险等级 | 问题 | 位置 |
|---------|------|------|
| 🔴 高 | `PictureService` 单类 ~800 行，职责过载 | `application/picture/PictureService.java` |
| 🔴 高 | `spaceRepository.save(space)` read-modify-write 并发竞态 | `PictureService.upload()` |
| 🔴 高 | 协同编辑锁仅存 JVM 内存，多实例部署直接失效 | `EditLockService.java` |
| 🔴 高 | `PictureResponseConverter.toResponse()` 签名与 `PictureResponse` 字段不匹配（`createdAt` 字段类型错误） | `PictureResponseConverter.java:9` |
| 🟡 中 | 搜索索引任务线程池无背压保护（`queueCapacity=500` 无界溢出后直接丢弃） | `SearchIndexConfig.java` |
| 🟡 中 | `TeamService` 内 N+1 查询（`countByTeamIdAndStatus` 在循环内） | `TeamService.listMyTeams()` |
| 🟡 中 | 无统一的 Outbox/事务消息，通知与业务在同一事务中耦合 | `PictureService.review()` |
| 🟡 中 | `@Cacheable` key 使用 `Arrays.asList(...)` 拼接，参数顺序脆弱 | 多处 Service |
| 🟢 低 | `application.yml` 硬编码 Redis 地址 `192.168.80.132` | `application.yml` |
| 🟢 低 | 无任何单元/集成测试（`TeamServiceTests` 语法错误） | `test/` 目录 |

---

## 二、三维视角评审

### 2.1 可维护性

**核心问题：`PictureService` 是全局反模式**

```
PictureService (800+ 行)
├── upload()              → 应抽 PictureUploadService
├── searchPictures()      → 应抽 PictureQueryService  
├── review()              → 应抽 ModerationService
├── listTags/addTags()    → 应抽 PictureTagService（已有但未被使用）
└── recommendPublic()     → 应抽 RecommendationService
```

`PictureResponseConverter.toResponse()` 存在硬编码 Bug：

```java
// 当前错误写法 — PictureResponse 无 String createdAt 字段
return new PictureResponse(
    asset.getId(), asset.getName(), asset.getUrl(),
    asset.getVisibility(), asset.getReviewStatus(),
    asset.getSizeBytes(), asset.getWidth(), asset.getHeight(),
    asset.getCreatedAt().toString()  // ← PictureResponse 无此字段，编译即失败
);
```

**`@Cacheable` key 脆弱性**：参数列表变更时旧缓存 key 不会自动失效，且无版本号：

```java
// 当前写法 — 脆弱
key = "T(java.util.Arrays).asList(#page,#size,#keyword,...)"

// 推荐写法 — 显式版本化
key = "'v1:search:' + #page + ':' + #size + ':' + #keyword"
```

### 2.2 可扩展性

**核心问题：有状态服务绑定单 JVM**

`EditLockService` 和 `PresenceService` 均使用 `ConcurrentHashMap` 存储状态，水平扩展时多实例数据不共享：

```
实例A (用户甲持锁) ←— 负载均衡 —→ 实例B (用户乙以为无锁)
                    ↑
              数据不同步，锁失效
```

**并发竞态**：`upload()` 中空间用量更新存在经典的 read-modify-write 问题：

```java
// 当前写法 — 并发不安全
space.setUsedBytes(space.getUsedBytes() + storageResult.getSizeBytes());
spaceRepository.save(space);

// 已有正确写法（DeduplicationPictureUploadService 中使用）
spaceRepository.incrementUsedBytes(space.getId(), fileContent.getSizeBytes());
```

**搜索索引无背压**：当上传高峰期队列满（500条）后，`ThreadPoolTaskExecutor` 默认策略是 `AbortPolicy`，任务直接丢弃且无告警。

**通知与业务强耦合**：`review()` 事务提交前调用 `notificationPublisher`，若通知发送失败会触发整个审核事务回滚：

```
BEGIN TRANSACTION
  → update picture_asset
  → insert moderation_record
  → notificationPublisher.notifyReviewDecision()  ← 失败 → 整个事务回滚
COMMIT
```

### 2.3 团队效率

**测试覆盖率：0%**（唯一测试文件有语法错误）

关键业务路径（审核流、团队邀请、去重上传）完全没有自动化验证，每次重构都是盲飞。

**领域对象泄漏**：Controller 层直接使用了多个领域枚举（`ReviewStatus`、`Visibility`、`TeamRole`），DTO 与领域层耦合，接口演进成本高。

**配置硬编码**：Redis、DB 地址写死在 `application.yml`，CI/CD 阶段难以无改动部署到不同环境。

---

## 三、演进路线图

### Phase 0 — 止血（第 1-2 周）

> 目标：消除高风险 Bug，不引入新功能

```
Week 1
├── 修复 PictureResponseConverter.toResponse() 编译 Bug
├── 修复 PictureService.upload() 并发竞态 → 替换为 incrementUsedBytes()
├── 配置外化：Redis/DB 地址改为环境变量
└── 搜索索引线程池：添加 RejectedExecutionHandler + 监控告警

Week 2  
├── TeamService.listMyTeams() N+1 修复
│   → 批量 countByTeamIdIn() 替代循环内单次查询
├── 通知从业务事务解耦
│   → ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT)
└── 统一 Cacheable key 格式，添加版本前缀
```

### Phase 1 — 重构（第 3-6 周）

> 目标：提升可维护性，建立测试基线

**Week 3-4：PictureService 拆分**

```
PictureService (保留核心协调逻辑)
├── PictureUploadService    → upload 相关
├── PictureQueryService     → search / listPublic / recommendations
├── ModerationService       → review / listPending / history
└── PictureTagService       → tag CRUD (复用现有 TagService 设计)
```

**Week 5：测试基线建立**

优先级排序（ROI 最高的先写）：

```
1. ModerationService 集成测试  → 覆盖状态流转核心路径
2. FileDeduplicationService 单元测试 → 覆盖并发 findOrCreate 场景
3. TeamService 邀请工作流集成测试
4. PictureQueryService 搜索过滤单元测试
```

测试策略：

```java
// 集成测试使用 @SpringBootTest + H2
@SpringBootTest(webEnvironment = NONE)
@ActiveProfiles("test")
class ModerationServiceTest {
    // 覆盖: PENDING→APPROVED, PENDING→REJECTED, 重复审核拒绝
}

// 并发测试使用 CountDownLatch
@Test
void concurrent_dedup_should_create_single_file_content() {
    // 20 个线程同时上传相同 SHA-256 的文件
    // 断言: file_content 表只有 1 条记录，ref_count = 20
}
```

**Week 6：DTO 边界清理**

```
interfaces/dto/ → 完全独立，不引用 domain 枚举
domain/ → 不引用任何 DTO
application/ → 负责 domain ↔ DTO 转换（Mapper 模式）
```

### Phase 2 — 扩展（第 7-12 周）

> 目标：支持多实例部署，协同编辑生产可用

**协同编辑状态外化（Week 7-8）**

```
当前：EditLockService (JVM 内存 ConcurrentHashMap)
目标：RedisEditLockService (Redis SETNX + TTL)

实现方案：
interface EditLockPort {
    boolean tryLock(UUID pictureId, UUID userId, String sessionId, Duration ttl);
    boolean releaseLock(UUID pictureId, UUID userId);
    LockInfo getLockInfo(UUID pictureId);
}

@Primary  // 生产
class RedisEditLockAdapter implements EditLockPort { ... }

@Profile("test")  // 测试  
class InMemoryEditLockAdapter implements EditLockPort { ... }
```

Redis Key 设计：

```
picture:lock:{pictureId}  →  Hash {userId, username, sessionId, expiresAt}
picture:presence:{pictureId}  →  Set of {sessionId:userId:username:joinedAt}
```

**搜索治理（Week 9-10）**

```
当前问题：全文搜索走 JPA like 查询，随数据增长性能下降
短期方案（不引入 ES）：
  → PostgreSQL GIN 索引 + tsvector 全文检索
  → picture_search_document.content 字段改为 tsvector 类型

迁移 SQL：
ALTER TABLE picture_search_document 
  ADD COLUMN content_tsv tsvector 
  GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;
CREATE INDEX idx_psd_content_tsv ON picture_search_document USING GIN(content_tsv);
```

**Outbox 模式引入（Week 11-12）**

解决通知与业务事务的最终一致性问题：

```
business_event_outbox 表:
  id, aggregate_type, aggregate_id, event_type, payload, status, created_at

事务内：INSERT INTO business_event_outbox (...)
事务后：@Scheduled 轮询 → 发布通知/事件 → 更新 status=PROCESSED
```

### Phase 3 — 治理（第 13-20 周）

> 目标：可观测性、CI/CD、长期可持续

**可观测性体系（Week 13-14）**

```
已有：Actuator + Prometheus + Micrometer
待补：
  → 自定义业务指标（审核耗时、上传成功率、搜索 P99）
  → 分布式 Trace (Micrometer Tracing + Zipkin/Tempo)
  → 结构化日志（MDC 注入 userId、requestId）
  → 告警规则（搜索索引积压、审核队列堆积）
```

**CI/CD 流水线（Week 15-16）**

```yaml
# .github/workflows/ci.yml
stages:
  - compile          # tsc + maven compile
  - test             # maven test (H2)  
  - integration-test # docker-compose PG + Redis
  - security-scan    # OWASP dependency check
  - build-image      # docker build
  - deploy-staging
```

**领域事件总线（Week 17-20）**

当业务复杂度继续增长时，引入领域事件解耦跨聚合协作：

```
PictureReviewedEvent
  → 触发通知
  → 触发搜索重索引
  → 触发统计更新

替代当前各 Service 内的直接调用链
```

---

## 四、优先级决策矩阵

| 事项 | 影响 | 成本 | 优先级 | 时间窗 |
|------|------|------|--------|--------|
| 修复并发竞态 Bug | 🔴 数据损坏 | 低 | P0 | Week 1 |
| 修复 Converter Bug | 🔴 编译失败 | 极低 | P0 | Week 1 |
| 通知解耦 | 🔴 事务回滚风险 | 低 | P0 | Week 2 |
| N+1 修复 | 🟡 性能 | 低 | P1 | Week 2 |
| 测试基线 | 🔴 重构安全网 | 中 | P1 | Week 5 |
| PictureService 拆分 | 🟡 可维护性 | 中 | P1 | Week 3-4 |
| EditLock 外化 Redis | 🔴 多实例必须 | 中 | P1 | Week 7-8 |
| PG 全文检索 | 🟡 性能 | 中 | P2 | Week 9-10 |
| Outbox 模式 | 🟡 一致性 | 中高 | P2 | Week 11-12 |
| 领域事件总线 | 🟢 扩展性 | 高 | P3 | Week 17-20 |

---

## 五、给团队的建议

**架构层面**：项目当前处于"骨架健康、肌肉薄弱"阶段。DDD 分层已到位，但领域对象的行为还没有充分内聚——大量业务逻辑堆积在 Service 层，实体仍是贫血模型。Phase 1 重构的本质是让"业务规则回归领域"。

**工程层面**：0 测试覆盖是当前最大的技术债。任何重构在没有测试保护的情况下都是高风险操作。Week 5 的测试基线建立，是后续所有演进的前提条件，建议作为团队里程碑节点。

**运维层面**：协同编辑功能在生产多实例部署时会立即暴露 `EditLockService` 的问题。如果近期有扩容计划，Week 7-8 的 Redis 外化应提前到 Phase 1 中执行。