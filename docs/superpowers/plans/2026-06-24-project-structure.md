# 优化后的项目结构规划

## 目标

将当前项目从原型级（50/100）提升至生产就绪级（75+/100），通过以下维度优化：

1. 清理冗余和临时文件
2. 补全缺失的工程文件
3. 规范化目录结构
4. 添加生产就绪配置

---

## 完整文件树（优化后）

```
cloud-picture-platform/
├── .github/
│   └── workflows/
│       └── ci.yml                          # [新增] GitHub Actions CI 流水线
│
├── .mvn/                                    # Maven Wrapper（保留）
│
├── ai-platform/                             # AI 微服务（保留，不改动）
│   ├── app/
│   ├── Dockerfile
│   └── pyproject.toml
│
├── agents/                                  # AI Agent 定义（保留）
│
├── docs/
│   └── superpowers/
│       └── plans/
│           └── 2026-06-24-architecture-optimization.md  # [新增] 本优化计划
│
├── hooks/                                   # Git hooks（保留）
│
├── references/                              # 参考文档（保留）
│
├── scripts/                                 # 脚本（保留）
│
├── skills/                                  # AI 技能定义（保留）
│
├── src/
│   ├── main/
│   │   ├── java/com/cn/cloudpictureplatform/
│   │   │   ├── CloudPicturePlatformApplication.java
│   │   │   │
│   │   │   ├── common/                      # 公共层
│   │   │   │   ├── exception/
│   │   │   │   │   └── ApiException.java
│   │   │   │   ├── model/
│   │   │   │   │   └── BaseEntity.java
│   │   │   │   ├── util/
│   │   │   │   │   └── CsvUtil.java
│   │   │   │   └── web/
│   │   │   │       ├── ApiErrorCode.java
│   │   │   │       ├── ApiResponse.java
│   │   │   │       ├── GlobalExceptionHandler.java
│   │   │   │       ├── PageRequestFactory.java
│   │   │   │       └── PageResponse.java
│   │   │   │
│   │   │   ├── config/                      # 配置层
│   │   │   │   ├── cache/
│   │   │   │   │   ├── FallbackCache.java
│   │   │   │   │   └── FallbackCacheManager.java
│   │   │   │   ├── CacheConfig.java
│   │   │   │   ├── CollaborationProperties.java
│   │   │   │   ├── CosProperties.java
│   │   │   │   ├── DataInitializer.java     # [修改] 修复 System.err → log
│   │   │   │   ├── JpaConfig.java
│   │   │   │   ├── JwtProperties.java
│   │   │   │   ├── SearchIndexConfig.java   # [修改] 增大线程池
│   │   │   │   ├── SecurityConfig.java      # [修改] 补全认证路径
│   │   │   │   ├── StorageProperties.java
│   │   │   │   ├── WebConfig.java
│   │   │   │   └── WebSocketConfig.java
│   │   │   │
│   │   │   ├── domain/                      # 领域层
│   │   │   │   ├── ai/
│   │   │   │   │   ├── AiCallAudit.java
│   │   │   │   │   ├── AiModerationRecord.java
│   │   │   │   │   └── AiTask.java
│   │   │   │   ├── album/
│   │   │   │   │   ├── Album.java
│   │   │   │   │   └── AlbumPicture.java
│   │   │   │   ├── apikey/
│   │   │   │   │   └── ApiKey.java
│   │   │   │   ├── audit/
│   │   │   │   │   └── ModerationRecord.java
│   │   │   │   ├── events/
│   │   │   │   │   ├── DomainEvent.java
│   │   │   │   │   ├── DomainEventBus.java
│   │   │   │   │   ├── PictureReviewedEvent.java
│   │   │   │   │   ├── PictureUploadedEvent.java
│   │   │   │   │   ├── TeamInviteEvent.java
│   │   │   │   │   └── TeamMemberJoinedEvent.java
│   │   │   │   ├── excalidraw/
│   │   │   │   │   ├── ExcalidrawFile.java
│   │   │   │   │   └── ExcalidrawScene.java
│   │   │   │   ├── notification/
│   │   │   │   │   └── NotificationRecord.java
│   │   │   │   ├── outbox/
│   │   │   │   │   ├── OutboxEvent.java
│   │   │   │   │   └── OutboxStatus.java
│   │   │   │   ├── picture/
│   │   │   │   │   ├── PictureAsset.java    # [修改] 添加软删除字段
│   │   │   │   │   ├── PictureComment.java
│   │   │   │   │   ├── PictureEditorDocument.java
│   │   │   │   │   ├── PictureTag.java
│   │   │   │   │   ├── PictureVersion.java
│   │   │   │   │   ├── ReviewStatus.java
│   │   │   │   │   ├── Tag.java
│   │   │   │   │   └── Visibility.java
│   │   │   │   ├── rbac/
│   │   │   │   │   ├── Menu.java
│   │   │   │   │   ├── Permission.java
│   │   │   │   │   ├── Role.java
│   │   │   │   │   ├── RoleMenu.java
│   │   │   │   │   ├── RoleMenuKey.java
│   │   │   │   │   ├── RolePermission.java
│   │   │   │   │   ├── RolePermissionKey.java
│   │   │   │   │   ├── UserRole.java
│   │   │   │   │   └── UserRoleKey.java
│   │   │   │   ├── search/
│   │   │   │   │   └── PictureSearchDocument.java
│   │   │   │   ├── space/
│   │   │   │   │   ├── Space.java
│   │   │   │   │   └── SpaceType.java
│   │   │   │   ├── storage/
│   │   │   │   │   ├── FileContent.java
│   │   │   │   │   ├── FileDeduplicationService.java
│   │   │   │   │   ├── ImageHashResult.java
│   │   │   │   │   ├── PerceptualHashService.java
│   │   │   │   │   ├── StorageResult.java
│   │   │   │   │   └── StorageService.java
│   │   │   │   ├── team/
│   │   │   │   │   ├── Team.java
│   │   │   │   │   ├── TeamActivity.java
│   │   │   │   │   ├── TeamMember.java
│   │   │   │   │   ├── TeamMemberEvent.java
│   │   │   │   │   └── TeamRole.java
│   │   │   │   ├── user/
│   │   │   │   │   ├── AppUser.java
│   │   │   │   │   └── UserStatus.java
│   │   │   │   └── watermark/
│   │   │   │       ├── ExportPreset.java
│   │   │   │       ├── ExportTask.java
│   │   │   │       └── WatermarkConfig.java
│   │   │   │
│   │   │   ├── application/                 # 应用层
│   │   │   │   ├── admin/
│   │   │   │   │   ├── AdminUserService.java
│   │   │   │   │   └── AiStatsService.java  # [新增] AI 统计服务
│   │   │   │   ├── album/
│   │   │   │   │   └── AlbumService.java
│   │   │   │   ├── apikey/
│   │   │   │   │   └── ApiKeyService.java
│   │   │   │   ├── auth/
│   │   │   │   │   └── AuthService.java
│   │   │   │   ├── collaboration/
│   │   │   │   │   └── PictureCollabAccessService.java
│   │   │   │   ├── events/
│   │   │   │   │   └── DomainEventSubscriber.java  # [修改] 修复 JSON 构建
│   │   │   │   ├── excalidraw/
│   │   │   │   │   └── ExcalidrawSceneService.java
│   │   │   │   ├── notification/
│   │   │   │   │   └── NotificationService.java
│   │   │   │   ├── outbox/
│   │   │   │   │   ├── OutboxProcessor.java
│   │   │   │   │   └── OutboxService.java
│   │   │   │   ├── picture/
│   │   │   │   │   ├── BatchPictureService.java      # [新增] 批量操作服务
│   │   │   │   │   ├── DeduplicationPictureUploadService.java
│   │   │   │   │   ├── ModerationService.java        # [修改] 添加权限校验
│   │   │   │   │   ├── OrphanFileCleanupTask.java
│   │   │   │   │   ├── PictureCollaborationRoomService.java
│   │   │   │   │   ├── PictureCommentService.java
│   │   │   │   │   ├── PictureDocumentService.java
│   │   │   │   │   ├── PictureQueryService.java
│   │   │   │   │   ├── PictureResponseConverter.java
│   │   │   │   │   ├── PictureTagService.java
│   │   │   │   │   ├── PictureUploadService.java
│   │   │   │   │   ├── PictureVersionService.java
│   │   │   │   │   └── SimilarImageDetectionService.java
│   │   │   │   ├── rbac/
│   │   │   │   │   ├── PermissionService.java
│   │   │   │   │   ├── RoleService.java
│   │   │   │   │   └── UserRoleService.java
│   │   │   │   ├── search/
│   │   │   │   │   ├── HybridSearchService.java
│   │   │   │   │   ├── SearchIndexService.java
│   │   │   │   │   └── SearchMaintenanceService.java
│   │   │   │   ├── space/
│   │   │   │   │   ├── SpacePermissionValidator.java
│   │   │   │   │   └── SpaceQuotaService.java
│   │   │   │   ├── tag/
│   │   │   │   │   └── TagService.java
│   │   │   │   ├── team/
│   │   │   │   │   ├── TeamActivityService.java
│   │   │   │   │   ├── TeamCommandService.java
│   │   │   │   │   ├── TeamMemberValidator.java      # [新增] 团队成员校验
│   │   │   │   │   └── TeamQueryService.java
│   │   │   │   └── watermark/
│   │   │   │       ├── ExportProcessingService.java   # [修改] 移除 failTask
│   │   │   │       ├── ExportTaskStatusUpdater.java   # [新增] 事务安全的状态更新
│   │   │   │       └── WatermarkService.java
│   │   │   │
│   │   │   ├── infrastructure/              # 基础设施层
│   │   │   │   ├── ai/
│   │   │   │   │   ├── AiGateway.java
│   │   │   │   │   ├── AiGatewayImpl.java
│   │   │   │   │   ├── AiChatRequest.java
│   │   │   │   │   ├── AiChatResponse.java
│   │   │   │   │   ├── AiModerationResultConsumer.java
│   │   │   │   │   ├── AiProperties.java
│   │   │   │   │   ├── AiQueueConfig.java
│   │   │   │   │   └── AiTaggingResultConsumer.java
│   │   │   │   ├── persistence/             # 34 个 Repository（保留全部）
│   │   │   │   │   ├── AlbumPictureRepository.java
│   │   │   │   │   ├── AlbumRepository.java
│   │   │   │   │   ├── AiCallAuditRepository.java
│   │   │   │   │   ├── AiModerationRecordRepository.java
│   │   │   │   │   ├── AiTaskRepository.java
│   │   │   │   │   ├── ApiKeyRepository.java
│   │   │   │   │   ├── AppUserRepository.java
│   │   │   │   │   ├── ExcalidrawFileRepository.java
│   │   │   │   │   ├── ExcalidrawSceneRepository.java
│   │   │   │   │   ├── ExportPresetRepository.java
│   │   │   │   │   ├── ExportTaskRepository.java
│   │   │   │   │   ├── FileContentRepository.java
│   │   │   │   │   ├── MenuRepository.java
│   │   │   │   │   ├── ModerationRecordRepository.java
│   │   │   │   │   ├── NotificationRecordRepository.java
│   │   │   │   │   ├── OutboxEventRepository.java
│   │   │   │   │   ├── PermissionRepository.java
│   │   │   │   │   ├── PictureAssetRepository.java
│   │   │   │   │   ├── PictureCommentRepository.java
│   │   │   │   │   ├── PictureEditorDocumentRepository.java
│   │   │   │   │   ├── PictureSearchDocumentRepository.java
│   │   │   │   │   ├── PictureTagRepository.java
│   │   │   │   │   ├── PictureVersionRepository.java
│   │   │   │   │   ├── RoleMenuRepository.java
│   │   │   │   │   ├── RolePermissionRepository.java
│   │   │   │   │   ├── RoleRepository.java
│   │   │   │   │   ├── SpaceRepository.java
│   │   │   │   │   ├── TagRepository.java
│   │   │   │   │   ├── TeamActivityRepository.java
│   │   │   │   │   ├── TeamMemberEventRepository.java
│   │   │   │   │   ├── TeamMemberRepository.java
│   │   │   │   │   ├── TeamRepository.java
│   │   │   │   │   ├── UserRoleRepository.java
│   │   │   │   │   ├── WatermarkConfigRepository.java
│   │   │   │   │   └── WebhookEndpointRepository.java
│   │   │   │   ├── search/
│   │   │   │   │   └── DatabaseSearchIndexService.java
│   │   │   │   ├── security/
│   │   │   │   │   ├── ApiKeyAuthFilter.java
│   │   │   │   │   ├── AppUserDetailsService.java
│   │   │   │   │   ├── AppUserPrincipal.java
│   │   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   │   ├── JwtTokenService.java
│   │   │   │   │   └── WebSocketAuthChannelInterceptor.java
│   │   │   │   └── storage/
│   │   │   │       ├── CosStorageService.java
│   │   │   │       └── LocalStorageService.java
│   │   │   │
│   │   │   ├── interfaces/                  # 接口层
│   │   │   │   ├── admin/
│   │   │   │   │   ├── AdminAiController.java       # [修改] 使用 AiStatsService
│   │   │   │   │   ├── AdminPermissionController.java
│   │   │   │   │   ├── AdminPictureController.java
│   │   │   │   │   ├── AdminRoleController.java
│   │   │   │   │   ├── AdminUserController.java
│   │   │   │   │   ├── AdminUserRoleController.java
│   │   │   │   │   ├── SearchAdminController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── AdminPictureSummary.java
│   │   │   │   │       ├── AdminUserSummary.java
│   │   │   │   │       ├── AiStatsResponse.java
│   │   │   │   │       ├── ModerationRecordResponse.java
│   │   │   │   │       ├── ReviewRequest.java
│   │   │   │   │       ├── SearchReindexResponse.java
│   │   │   │   │       └── rbac/
│   │   │   │   │           ├── PermissionCreateRequest.java
│   │   │   │   │           ├── PermissionResponse.java
│   │   │   │   │           ├── PermissionUpdateRequest.java
│   │   │   │   │           ├── RoleCreateRequest.java
│   │   │   │   │           ├── RolePermissionUpdateRequest.java
│   │   │   │   │           ├── RoleResponse.java
│   │   │   │   │           ├── RoleUpdateRequest.java
│   │   │   │   │           ├── UserRoleAssignRequest.java
│   │   │   │   │           ├── UserRoleBulkUpdateRequest.java
│   │   │   │   │           └── UserRoleResponse.java
│   │   │   │   ├── ai/
│   │   │   │   │   ├── AiAssistantController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       └── AiChatRequest.java
│   │   │   │   ├── album/
│   │   │   │   │   ├── AlbumController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── AlbumCreateRequest.java
│   │   │   │   │       └── AlbumResponse.java
│   │   │   │   ├── auth/
│   │   │   │   │   ├── AuthController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── AuthResponse.java
│   │   │   │   │       ├── LoginRequest.java
│   │   │   │   │       ├── RegisterRequest.java
│   │   │   │   │       ├── UserInfoResponse.java
│   │   │   │   │       └── UserProfileUpdateRequest.java
│   │   │   │   ├── developer/
│   │   │   │   │   └── DeveloperController.java
│   │   │   │   ├── excalidraw/
│   │   │   │   │   ├── ExcalidrawSceneController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── CreateExcalidrawSceneRequest.java
│   │   │   │   │       ├── ExcalidrawSceneResponse.java
│   │   │   │   │       └── UpdateSnapshotRequest.java
│   │   │   │   ├── notification/
│   │   │   │   │   ├── NotificationController.java
│   │   │   │   │   └── NotificationResponse.java
│   │   │   │   ├── picture/
│   │   │   │   │   ├── BatchPictureController.java   # [修改] 使用 BatchPictureService
│   │   │   │   │   ├── DeduplicationPictureController.java
│   │   │   │   │   ├── ImageSearchController.java
│   │   │   │   │   ├── PictureCommentController.java
│   │   │   │   │   ├── PictureController.java
│   │   │   │   │   ├── PictureVersionController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── BatchOperationRequest.java
│   │   │   │   │       ├── CommentCreateRequest.java
│   │   │   │   │       ├── CommentResponse.java
│   │   │   │   │       ├── EditorRealtimeContractResponse.java
│   │   │   │   │       ├── EditorRealtimeEventDefinitionResponse.java
│   │   │   │   │       ├── PictureCollaborationRoomResponse.java
│   │   │   │   │       ├── PictureDetailResponse.java
│   │   │   │   │       ├── PictureDocumentElementResponse.java
│   │   │   │   │       ├── PictureEditorDocumentResponse.java
│   │   │   │   │       ├── PictureEditorSessionResponse.java
│   │   │   │   │       ├── PictureResponse.java
│   │   │   │   │       ├── PictureSummary.java
│   │   │   │   │       ├── PictureTagCreateRequest.java
│   │   │   │   │       ├── PictureTagItemRequest.java
│   │   │   │   │       ├── PictureTagResponse.java
│   │   │   │   │       ├── PictureVersionResponse.java
│   │   │   │   │       └── RapidUploadCheckResponse.java
│   │   │   │   ├── space/
│   │   │   │   │   └── SpaceController.java
│   │   │   │   ├── tag/
│   │   │   │   │   ├── TagController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── TagCreateRequest.java
│   │   │   │   │       ├── TagResponse.java
│   │   │   │   │       └── TagUpdateRequest.java
│   │   │   │   ├── team/
│   │   │   │   │   ├── TeamActivityController.java
│   │   │   │   │   ├── TeamController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── TeamCreateRequest.java
│   │   │   │   │       ├── TeamInviteRequest.java
│   │   │   │   │       ├── TeamInviteSummaryResponse.java
│   │   │   │   │       ├── TeamMemberEventResponse.java
│   │   │   │   │       ├── TeamMemberResponse.java
│   │   │   │   │       ├── TeamResponse.java
│   │   │   │   │       ├── TeamRoleUpdateRequest.java
│   │   │   │   │       ├── TeamSummaryResponse.java
│   │   │   │   │       └── TeamUpdateRequest.java
│   │   │   │   └── watermark/
│   │   │   │       ├── WatermarkController.java
│   │   │   │       └── dto/
│   │   │   │           └── WatermarkConfigRequest.java
│   │   │   │
│   │   │   └── websocket/                   # WebSocket 层
│   │   │       ├── dto/
│   │   │       │   ├── CollabMessage.java
│   │   │       │   ├── EditorCursorPayload.java
│   │   │       │   ├── EditorSelectionPayload.java
│   │   │       │   ├── NotificationMessage.java
│   │   │       │   ├── PictureDocumentOperationPayload.java
│   │   │       │   └── PresenceSnapshot.java
│   │   │       ├── EditLockPort.java
│   │   │       ├── EditLockService.java
│   │   │       ├── NotificationPublisher.java
│   │   │       ├── PictureCollabController.java
│   │   │       └── PresenceService.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml              # [修改] 安全化默认值
│   │       ├── application-dev.yml          # [新增] 开发环境配置
│   │       ├── application-prod.yml         # [新增] 生产环境配置
│   │       └── db/migration/
│   │           ├── V1__init.sql
│   │           ├── V2__rbac.sql
│   │           ├── V13__rbac_management.sql
│   │           ├── V14__outbox.sql
│   │           ├── V15__fulltext_search.sql
│   │           ├── V16__album.sql
│   │           ├── V17__picture_version.sql
│   │           ├── V18__picture_comment.sql
│   │           ├── V19__team_activity.sql
│   │           ├── V20__watermark_export.sql
│   │           ├── V21__webhook.sql
│   │           ├── V22__api_key.sql
│   │           ├── V23__ai.sql
│   │           ├── V24__ai_moderation.sql
│   │           ├── V25__vector_search.sql
│   │           ├── V26__user_behavior.sql
│   │           ├── V27__menu.sql
│   │           ├── V28__create_excalidraw_tables.sql  # [修改] 修复表名
│   │           ├── V29__create_notification_record.sql # [重命名] 原 V13
│   │           ├── V30__drop_legacy_role_column.sql   # [重命名] 原 V15
│   │           ├── V31__force_drop_legacy_role_column.sql # [重命名] 原 V16
│   │           ├── V32__add_missing_foreign_keys.sql  # [新增] 补全 FK
│   │           └── V33__add_soft_delete_to_picture.sql # [新增] 软删除
│   │
│   └── test/
│       ├── java/com/cn/cloudpictureplatform/
│       │   ├── CloudPicturePlatformApplicationTests.java
│       │   ├── architecture/
│       │   │   └── LayerDependencyRulesTest.java      # [修改] 扩展规则
│       │   ├── application/
│       │   │   ├── collaboration/
│       │   │   │   └── PictureCollabAccessServiceTests.java
│       │   │   ├── excalidraw/
│       │   │   │   └── ExcalidrawSceneServiceTests.java
│       │   │   ├── picture/
│       │   │   │   ├── PictureDocumentServiceTests.java
│       │   │   │   └── PictureUploadIntegrationTest.java  # [新增] 集成测试
│       │   │   └── team/
│       │   │       └── TeamServiceTests.java
│       │   ├── infrastructure/
│       │   │   └── security/
│       │   │       └── WebSocketAuthChannelInterceptorTests.java
│       │   └── websocket/
│       │       └── PictureCollabControllerTests.java
│       └── resources/
│           └── application.yml
│
├── AGENTS.md
├── Dockerfile                               # [新增] 应用 Docker 镜像
├── docker-compose.yml                       # [修改] 添加 app 服务
├── mvnw / mvnw.cmd
├── pom.xml
└── README.md
```

---

## 清理清单（应删除的文件）

| 文件 | 原因 |
|------|------|
| `nul` | 空文件，可能是 Windows 误操作 |
| `.idea/` | IDE 配置，不应提交到 Git |
| `cloud-picture-platform.iml` | IDE 配置 |
| `data/` | 上传文件目录，应在 `.gitignore` 中 |
| `target/` | 构建产物，应在 `.gitignore` 中 |

---

## 需要更新的 .gitignore 规则

```gitignore
# IDE
.idea/
*.iml

# Build
target/

# Data
data/uploads/
data/test-uploads/

# OS
Thumbs.db
.DS_Store
nul
```

---

## 新增文件统计

| 类别 | 数量 | 文件 |
|------|------|------|
| 新增服务类 | 4 | BatchPictureService, AiStatsService, TeamMemberValidator, ExportTaskStatusUpdater |
| 新增配置文件 | 3 | application-dev.yml, application-prod.yml, ci.yml |
| 新增迁移脚本 | 2 | V32 (FK), V33 (soft delete) |
| 重命名迁移 | 3 | V29, V30, V31 |
| 修改迁移 | 1 | V28 (表名修复) |
| 新增测试 | 1 | PictureUploadIntegrationTest |
| 新增部署文件 | 1 | Dockerfile |
| **总计新增/修改** | **15** | |

---

## 架构改进总结

### 修改前

```
interfaces → domain ← infrastructure
     ↓
  application
```

（实际违反：控制器直接注入 Repository，事务在控制器层）

### 修改后

```
interfaces → application → domain ← infrastructure
     ↓              ↓
  (薄层)      (事务边界)
```

（严格遵循：控制器只调用服务，服务管理事务，领域层无外部依赖）
