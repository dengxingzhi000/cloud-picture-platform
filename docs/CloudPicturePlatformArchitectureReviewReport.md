# Cloud Picture Platform 架构评审报告

## 执行摘要

基于对全量代码的审阅，项目整体架构骨架清晰，DDD 分层到位，但在**领域边界、并发安全、可观测性、安全控制、测试覆盖**五个维度存在系统性欠缺。本次审计新增 22 个发现项（含 7 项高风险），与原报告合并后总计 32 项问题（11 项高风险、14 项中风险、7 项低风险）。以下评审从五个核心视角展开，并给出带时间节点的演进路线。

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
| 🔴 高 | `PictureService` 单类 ~800 行，职责过载 | `application/picture/PictureService.java`（已拆分） |
| 🔴 高 | `TeamService` 单类 ~825 行，职责过载（读写混合、权限校验、CSV 导出全在一个类） | `application/team/TeamService.java` |
| 🔴 高 | `spaceRepository.save(space)` read-modify-write 并发竞态 | `PictureService.upload()`（已修复） |
| 🔴 高 | 协同编辑锁仅存 JVM 内存，多实例部署直接失效 | `EditLockService.java` |
| 🔴 高 | `DatabaseSearchIndexService` 自调用绕过 `@Transactional` —— `indexPicture()` 在 `indexWithRetry()` 内直接调用，AOP 代理不生效，事务实际未开启 | `infrastructure/search/DatabaseSearchIndexService.java:84` |
| 🔴 高 | `AuthController` 直接注入 `AppUserRepository` 并在 Controller 层组装响应，绕过了 Application 层 | `interfaces/auth/AuthController.java:29,47` |
| 🔴 高 | WebSocket Admin 主题无访问控制（`/topic/admin/notifications`、`/topic/admin/reviews`），任意连接用户可订阅 | `WebSocketAuthChannelInterceptor.java` / `NotificationPublisher.java:79,90` |
| 🔴 高 | WebSocket 认证失败时静默放行——无 token/token 无效/用户不存在的情形全部通过，不做任何处理 | `WebSocketAuthChannelInterceptor.java:60-67,87-89` |
| 🔴 高 | `AuthService.buildPrincipal()` 循环内逐条 `findById()` 加载 Role（N+1 查询） | `application/auth/AuthService.java:156-160` |
| 🔴 高 | `RoleService.updateRolePermissions()` 先 `deleteAll` 再 `saveAll`，崩溃后角色零权限 | `application/rbac/RoleService.java:109-130` |
| 🔴 高 | `PictureResponseConverter.toResponse()` 签名与 `PictureResponse` 字段不匹配（`createdAt` 字段类型错误） | `PictureResponseConverter.java:9`（已修复） |
| 🟡 中 | 搜索索引任务线程池无背压保护（`queueCapacity=500` 无界溢出后直接丢弃） | `SearchIndexConfig.java` |
| 🟡 中 | Application 层直接引用 interfaces 层 DTO（`PictureSummary`、`TeamCreateRequest`、`AuthResponse` 等），违反六边形架构依赖规则 | 多处 application 服务 |
| 🟡 中 | 空间权限校验逻辑在三处重复（`PictureUploadService`、`DeduplicationPictureUploadService`、`SpacePermissionValidator`），DRY 违反 | `application/picture/` |
| 🟡 中 | 大部分查询服务缺少 `@Transactional(readOnly = true)`，存在 `LazyInitializationException` 风险 | `PictureQueryService`、`ModerationService`、`TeamService` 等 |
| 🟡 中 | `GlobalExceptionHandler` 未处理 `ConstraintViolationException`、`MethodArgumentNotValidException`、`AccessDeniedException`，可能泄漏技术细节 | `common/web/GlobalExceptionHandler.java` |
| 🟡 中 | `@Min`/`@Max` 参数校验注解缺少类级 `@Validated`，校验注解被静默忽略 | `TagController`、`AuthController`、`AdminUserRoleController` |
| 🟡 中 | `CosStorageService` 无路径遍历防护（`key` 未 `normalize()`，恶意 `../../secrets` 可访问非预期路径） | `infrastructure/storage/CosStorageService.java:49` |
| 🟡 中 | Dev 配置 `application-dev.yml` 未禁用 Redis，即使切换 H2 仍尝试连接 Redis | `application-dev.yml` |
| 🟡 中 | `PictureController` 手动构造器注入（8 个参数），与其他 `@RequiredArgsConstructor` 控制器不一致 | `interfaces/picture/PictureController.java:65-83` |
| 🟡 中 | `PictureController` 依赖 `websocket` 包（`PictureCollabAccessService`、`PresenceService`、`EditLockService`），HTTP 层不应依赖 WebSocket 层 | `interfaces/picture/PictureController.java:44-47` |
| 🟡 中 | `PictureCollabController.refreshLock` 未校验调用方身份，任意用户可延长锁持有者 TTL | `websocket/PictureCollabController.java:285` |
| 🟡 中 | `PresenceService` 中 `sessionIndex.put()` 在 `ConcurrentHashMap.computeIfAbsent` 原子操作之外，并发断开导致状态不一致 | `websocket/PresenceService.java:34-38` |
| 🟡 中 | `FileDeduplicationService.decrementRefCount()` 执行 `@Modifying` 后未 `refresh()` EntityManager，缓存可能返回脏数据 | `domain/storage/FileDeduplicationService.java:176-188` |
| 🟡 中 | `OrphanFileCleanupTask` 全局 `@Transactional` 包裹批量循环，单条失败导致整体回滚 | `application/picture/OrphanFileCleanupTask.java:32-71` |
| 🟢 低 | `application.yml` 硬编码 Redis 地址 `192.168.80.132` | `application.yml` |
| 🟢 低 | 无任何单元/集成测试（`TeamServiceTests` 语法错误） | `test/` 目录 |
| 🟢 低 | `PictureUploadService.computeChecksum()` 使用 MD5 而非 SHA-256，与 `DeduplicationPictureUploadService` 不一致 | `application/picture/PictureUploadService.java:141` |
| 🟢 低 | `UserRole` 与 `RolePermission` 的 `@ManyToOne(fetch = LAZY)` 可能导致 `LazyInitializationException` | `domain/rbac/UserRole.java:41`、`RolePermission.java:41` |
| 🟢 低 | `AuthService.buildPrincipal()` 将所有权限名称塞入 JWT claims，对于大权限集导致 token 膨胀 | `application/auth/AuthService.java:162-163` |
| 🟢 低 | `DataInitializer` 创建 admin 用户时若 `ROLE_ADMIN` 不存在则静默降级（无角色创建逻辑） | `config/DataInitializer.java:61` |
| 🟢 低 | CSV 导出逻辑内联在 Controller 中（`toCsv()`/`escapeCsv()`），应提取为公共转换器 | `AdminPictureController.java:153-190`、`TeamController.java:278-321` |

---

## 二、五维视角评审

### 2.1 可维护性

**核心问题：巨型 Service 是全局反模式**

`PictureService`（~800 行）已完成拆分，但 `TeamService`（~825 行）仍然存在同样问题：

```
TeamService (825+ 行)
├── 团队 CRUD（create/update/delete）
├── 成员管理（invite/accept/remove/quit）
├── 权限校验（checkPermission/isAdmin）
├── 事件记录（listEvents/exportEvents - CSV 内联）
├── 响应映射（Entity→DTO 转换内联）
└── 排序逻辑（sortMembers/sortTeams）
```

此外，Application 层与 interfaces 层存在**双向依赖**（原本应该是 Application→Domain，Interfaces→Application）：

```
Application 层导入:
  interfaces/picture/dto/PictureSummary      ← 违反！
  interfaces/team/dto/TeamCreateRequest      ← 违反！
  interfaces/admin/dto/AdminPictureSummary   ← 违反！
```

Controller 层也存在不一致：`PictureController` 手动注入 8 个依赖（含 WebSocket 服务），而其他 Controller 均使用 `@RequiredArgsConstructor`。

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

**核心问题 1：有状态服务绑定单 JVM**

`EditLockService` 和 `PresenceService` 均使用 `ConcurrentHashMap` 存储状态，水平扩展时多实例数据不共享：

```
实例A (用户甲持锁) ←— 负载均衡 —→ 实例B (用户乙以为无锁)
                    ↑
              数据不同步，锁失效
```

**核心问题 2：事务边界漏洞**

`DatabaseSearchIndexService` 存在经典的自调用绕过事务问题：

```java
// DatabaseSearchIndexService.java:81-84
public void indexWithRetry(...) {
    // ← 自调用：AOP 代理不生效
    this.indexPicture(pictureId, content);  // @Transactional 被静默忽略！
}

@Transactional
public void indexPicture(...) {  // 事务注解不生效
    searchDocumentRepository.save(...);
}
```

**并发竞态**：`upload()` 中空间用量更新存在经典的 read-modify-write 问题（已在 Phase 0 修复）：

```java
// 当前写法 — 并发不安全（已修复）
space.setUsedBytes(space.getUsedBytes() + storageResult.getSizeBytes());
spaceRepository.save(space);

// 已有正确写法（DeduplicationPictureUploadService 中使用）
spaceRepository.incrementUsedBytes(space.getId(), fileContent.getSizeBytes());
```

`RoleService.updateRolePermissions()` 的非原子操作：先 `deleteAll` 再 `saveAll`，中间崩溃导致角色零权限：

```java
// 当前 — 非原子
rolePermissionRepository.deleteAll(existing);  // 已删除
// ← 系统崩溃 → 角色没有任何权限
rolePermissionRepository.saveAll(newPermissions);
```

`OrphanFileCleanupTask` 中 `@Transactional` 包裹整个循环，单文件清理失败回滚全部：

```java
@Transactional  // 整个批次在一个事务中
public void cleanupOrphanFiles() {
    for (...) {  // 任何一次失败 → 全部回滚
        deleteFromStorage(file);
        fileContentRepository.delete(fileContent);
    }
}
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

**CSV 导出逻辑重复**：`AdminPictureController` 与 `TeamController` 各自实现 `toCsv()`/`escapeCsv()`，抽离为公共组件可避免不一致。

### 2.4 安全性

**WebSocket 安全存在系统性缺陷**：

1. **HTTP 层全开放**：`SecurityConfig.java` 配置 `.requestMatchers("/ws/**").permitAll()`，WebSocket 升级端点无认证
2. **Interceptor 静默放行**：缺失/无效 token（第 60-67 行）和未知用户（第 87-89 行）均不做拦截，连接正常建立
3. **Admin 主题无保护**：`/topic/admin/notifications` 和 `/topic/admin/reviews` 无订阅权限校验，任意已连接用户可订阅敏感通知

```
攻击路径：
ws.connect()                   → 不携带 token 或携带无效 token
  → WebSocketAuthChannelInterceptor 放行（userId = null）
    → 订阅 /topic/admin/notifications
      → 持续接收所有审核决策通知（信息泄露）
```

**COS 存储路径遍历**：`CosStorageService` 对 `key` 参数未做 `normalize()` 和 `startsWith` 校验，恶意 `../../` 路径可访问非预期对象。

**密码策略缺失**：注册接口无密码强度校验（长度、大小写、数字、特殊字符），用户可设置弱密码。

### 2.5 可观测性

**异常处理不完整**：`GlobalExceptionHandler` 未注册 `ConstraintViolationException`、`MethodArgumentNotValidException`、`AccessDeniedException`、`HttpMessageNotReadableException`，Spring 默认响应可能泄漏技术细节（如堆栈片段）。

**日志上下文缺失**：无 MDC（Mapped Diagnostic Context）注入，请求链路中无法关联 `requestId`、`userId`、`pictureId` 等上下文信息，问题排查依赖全文搜索日志。

**业务指标空白**：虽然有 Actuator + Micrometer + Prometheus，但无自定义业务指标（审核耗时、上传成功率、搜索 P99、队列积压深度），无法主动感知系统健康度。

**`CacheConfig` 启动强依赖 Redis**：即使启用了 `FallbackCacheManager`，`RedisCacheManager` 的 bean 创建在启动阶段完成，Redis 不可用时应用直接启动失败，降级机制在启动阶段无效。

---

## 三、演进路线图

### Phase 0 — 止血（第 1-2 周）

> 目标：消除高风险 Bug + 安全漏洞，不引入新功能

```
Week 1
├── 修复 DatabaseSearchIndexService 自调用事务问题
│   → 分离 @Transactional 方法到独立 Bean，或使用 self-inject 代理
├── AuthController 业务逻辑下沉 → 移到 AuthService
├── 修复 RoleService.updateRolePermissions() 非原子操作
│   → diff 计算增量变更，或 try-catch 回滚
├── WebSocket Admin 主题添加订阅权限校验
│   → WebSocketAuthChannelInterceptor 中校验 /topic/admin/** 需 ROLE_ADMIN
├── WebSocket 认证失败改为拒绝连接（而非静默放行）
├── 修复 CosStorageService 路径遍历漏洞 → normalize + startsWith 校验
└── 搜索索引线程池：添加 RejectedExecutionHandler + 监控告警

Week 2
├── TeamService N+1 修复 → 批量查询替代循环内单条
├── 空间权限校验逻辑合并 → 统一由 SpacePermissionValidator 提供
├── 通知从业务事务解耦
│   → ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT)
├── 统一 Cacheable key 格式，添加版本前缀
├── AuthService.buildPrincipal() Role N+1 → findAllById() 批量加载
├── OrphanFileCleanupTask 逐条事务 → @Transactional(propagation = REQUIRES_NEW)
├── FileDeduplicationService decrementRefCount 后追加 refresh()
├── PresenceService 会话状态写入纳入原子操作
└── 全局缺失的 @Transactional(readOnly = true) 补充
```

### Phase 1 — 重构（第 3-6 周）

> 目标：提升可维护性，消除层依赖泄露，建立测试基线

**Week 3-4：Service 拆分 + 层依赖清理**

```
TeamService (825+ 行) 拆分：
├── TeamQueryService       → 只读操作（listMyTeams, getDetail, listMembers, listEvents）
├── TeamCommandService     → 写操作（create, update, delete, invite, accept, remove）
├── TeamEventService       → 事件记录与导出（CSV 逻辑提取到导出工具类）
└── TeamPermissionService  → 权限校验（checkPermission, isAdmin）

层依赖清理：
├── Application 层移除对 interfaces DTO 的所有引用
│   → 每个 Application 服务定义自己的内部 DTO 或使用领域对象
├── PictureController 移除对 websocket 包的依赖
│   → PictureCollabAccessService 上移到 application 层
├── PictureController 统一为 @RequiredArgsConstructor
└── CSV 导出逻辑提取到公共工具类
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

> 目标：支持多实例部署，协同编辑生产可用，安全加固

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

**安全加固（Week 9-10）**

```
已完成：
├── WebSocket Admin 主题 ACL（Phase 0 紧急修复）
├── COS 路径遍历防护（Phase 0 紧急修复）
├── 认证失败拒绝连接（Phase 0 紧急修复）

待完成：
├── 密码强度校验 → spring-security 密码编码器策略 + 注册校验
├── SecurityConfig 层 WebSocket 仅放行 CONNECT，/ws/info 等端点可选认证
├── @Validated 类级注解补充至所有 Controller
├── GlobalExceptionHandler 补充所有缺失的异常处理器
├── DataInitializer 角色不存在时自动创建（而非静默降级）
├── JWT 权限声明可选加载（按需而非全量）
└── UserRole/RolePermission 的 LAZY 加载优化为 @EntityGraph
```

**搜索治理（Week 9-10 并行）**

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
  → GlobalExceptionHandler 全面覆盖：
      - ConstraintViolationException → 400
      - MethodArgumentNotValidException → 400
      - AccessDeniedException → 403
      - HttpMessageNotReadableException → 400
      - TypeMismatchException → 400
      - MissingServletRequestPartException → 400
  → CacheConfig 启动容忍 Redis 不可用
      - @ConditionalOnBean(RedisConnectionFactory.class) 保护 RedisCacheManager
      - 或通过 @PostConstruct 延迟初始化
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
| 修复自调用事务 Bug | 🔴 数据不一致 | 低 | P0 | Week 1 |
| WebSocket Admin 主题 ACL | 🔴 信息泄露 | 低 | P0 | Week 1 |
| WebSocket 认证拒绝放行 | 🔴 认证绕过 | 低 | P0 | Week 1 |
| COS 路径遍历防护 | 🔴 越权访问 | 低 | P0 | Week 1 |
| RoleService 非原子更新 | 🔴 数据损坏 | 低 | P0 | Week 1 |
| 修复并发竞态 Bug | 🔴 数据损坏 | 低 | P0 | Week 1 |
| 修复 Converter Bug | 🔴 编译失败 | 极低 | P0 | Week 1（已修复） |
| 通知解耦 | 🔴 事务回滚风险 | 低 | P0 | Week 2 |
| 空间权限校验去重 | 🟡 DRY 违反 | 低 | P1 | Week 2 |
| N+1 修复（TeamService/AuthService） | 🟡 性能 | 低 | P1 | Week 2 |
| 补充 @Transactional(readOnly=true) | 🟡 性能 + LazyInit | 低 | P1 | Week 2 |
| OrphanFileCleanup 逐条事务 | 🟡 批量回滚风险 | 低 | P1 | Week 2 |
| 测试基线 | 🔴 重构安全网 | 中 | P1 | Week 5 |
| TeamService 拆分 | 🟡 可维护性 | 中 | P1 | Week 3-4 |
| Application 层 DTO 依赖清理 | 🟡 层污染 | 中 | P1 | Week 4 |
| EditLock 外化 Redis | 🔴 多实例必须 | 中 | P1 | Week 7-8 |
| 安全加固（密码/异常处理/LAZY） | 🟡 安全 | 中 | P2 | Week 9-10 |
| PG 全文检索 | 🟡 性能 | 中 | P2 | Week 9-10 |
| Outbox 模式 | 🟡 一致性 | 中高 | P2 | Week 11-12 |
| 可观测性体系 | 🟢 运维 | 中 | P2 | Week 13-14 |
| CI/CD 流水线 | 🟢 自动化 | 中 | P3 | Week 15-16 |
| 领域事件总线 | 🟢 扩展性 | 高 | P3 | Week 17-20 |

---

## 五、给团队的建议

**架构层面**：项目当前处于"骨架健康、肌肉薄弱"阶段。DDD 分层已到位，但领域对象的行为还没有充分内聚——大量业务逻辑堆积在 Service 层，实体仍是贫血模型。Phase 1 重构的本质是让"业务规则回归领域"。

**工程层面**：0 测试覆盖是当前最大的技术债。任何重构在没有测试保护的情况下都是高风险操作。Week 5 的测试基线建立，是后续所有演进的前提条件，建议作为团队里程碑节点。

**运维层面**：协同编辑功能在生产多实例部署时会立即暴露 `EditLockService` 的问题。如果近期有扩容计划，Week 7-8 的 Redis 外化应提前到 Phase 1 中执行。

**安全层面**：WebSocket 的认证绕过和 Admin 主题 ACL 缺失是当前最紧急的防线漏洞。Phase 0 必须优先修复，建议在 Week 1 完成前阻断所有已知攻击路径。后续应建立安全评审卡点，关键变更（认证、授权、数据校验、文件上传）在合并前均需安全审查。

**工程实践层面**：
- 建议引入 ArchUnit 测试，在 CI 中自动校验层依赖规则（Application 不能引用 interfaces 等），从工具层面杜绝层污染复发
- `@Transactional(readOnly = true)` 应作为所有只读 Service 方法的默认配置，避免 `LazyInitializationException` 的"警匪追逐"式修复
- 建议为 Service 方法建立自调用检测机制（`@SelfInvocationSafe` 注解 + Aspect 告警），防止事务注解因自调用静默失效