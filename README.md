# CloudPicturePlatform - 云图片协作平台

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/dengxingzhi000/cloud-picture-platform?style=flat-square&color=green)](https://github.com/dengxingzhi000/cloud-picture-platform)
[![GitHub Forks](https://img.shields.io/github/forks/dengxingzhi000/cloud-picture-platform?style=flat-square&color=blue)](https://github.com/dengxingzhi000/cloud-picture-platform)
[![License](https://img.shields.io/badge/license-Apache%202.0-red?style=flat-square)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21-brightgreen?style=flat-square)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/SpringBoot-4.0.1-orange?style=flat-square)](https://spring.io/projects/spring-boot)

基于 Spring Boot 4 + Java 21 的企业级云图片协作平台，支持公开画廊、私人空间、团队协作与 AI 智能标签，开箱即用。

[简介](#简介) • [核心功能](#核心功能) • [技术栈](#技术栈) • [快速开始](#快速开始) • [项目结构](#项目结构) • [路线图](#路线图) • [贡献指南](#贡献指南)

</div>

---

## 简介

**CloudPicturePlatform** 是一个基于 Spring Boot 4.0.1 + Java 21 构建的企业级云图片管理与协作平台。采用领域驱动设计（DDD）分层架构，提供公开画廊、私人图库、团队空间三大使用场景，内置内容审核工作流、AI 辅助标签生成与多级缓存体系，支持本地存储与腾讯云 COS 双存储后端。

### 🎯 核心优势

- ✨ **多场景支持** — 公开画廊 / 私人图库 / 团队协作空间，满足个人与企业需求
- 🔐 **开箱即用的安全体系** — JWT 无状态认证，RBAC 角色权限，平台角色与空间角色双维度管控
- 🏷️ **AI 智能标签** — 上传时自动调用 AI 服务生成标签，支持多提供商（OpenAI / Google Vision / AWS Rekognition）
- ⚡ **高性能缓存** — Caffeine（L1）+ Redis（L2）两级缓存，Redis 不可用时自动降级
- 🗄️ **弹性存储** — 一键切换本地文件系统与腾讯云 COS，接口统一无感迁移
- 📋 **完整审计链路** — 内容审核工作流、成员操作事件、标签变更全量审计
- ☁️ **云原生就绪** — Docker 友好，环境变量配置，Flyway 数据库版本管理

---

## 核心功能

### 🔒 身份与访问控制
- **JWT 认证** — 无状态令牌，2小时有效期，Spring Security 集成
- **RBAC 权限模型** — 平台角色（USER / ADMIN）+ 空间角色（OWNER / ADMIN / MEMBER）
- **细粒度鉴权** — 上传、审核、编辑、导出各操作独立权限控制
- **管理端隔离** — `/api/admin/**` 端点强制 ADMIN 角色，网关层拦截

### 🖼️ 图片资产管理
- **上传管道** — 文件校验 → 存储 → MD5 校验和 → 图像尺寸提取 → 搜索索引
- **多可见性模式** — PUBLIC（公开）/ PRIVATE（私有）/ TEAM（团队可见）
- **元数据提取** — 分辨率、文件大小、格式、校验和自动记录
- **空间配额管理** — 每个用户/团队拥有独立 Space，实时追踪 `usedBytes`

### 🏷️ 标签与搜索
- **标签目录** — 全局 Tag 目录，支持创建、改名（级联更新所有关联）、删除（使用中禁止）
- **图片标签关联** — `PictureTag` 记录置信度评分与 AI 提供商信息
- **多维搜索** — 关键词、标签、可见性、审核状态、拥有者、空间、日期范围、图片方向
- **搜索索引** — `PictureSearchDocument` 异步维护，支持后台全量重建

### 👥 团队协作
- **团队生命周期** — 创建自动生成 TEAM 类型空间和 OWNER 成员记录
- **邀请工作流** — 发送邀请 → 接受 / 拒绝 → 状态流转，支持取消和移除
- **角色管理** — OWNER / ADMIN / MEMBER 三级角色，权限分级控制
- **成员事件审计** — JOINED / INVITED / LEFT / MEMBER_REMOVED / TEAM_UPDATED 全量事件日志

### 📋 内容审核
- **审核工作流** — 公开图片上传后进入 PENDING 状态，等待管理员审批
- **审核操作** — APPROVE / REJECT，支持附加原因说明
- **审核历史** — `ModerationRecord` 记录完整审核链路，支持分页查询与 CSV 导出
- **批量操作** — 管理员批量处理待审图片

### ⚡ 性能与缓存
- **两级缓存** — Caffeine 本地缓存（L1）+ Redis 分布式缓存（L2）
- **降级容错** — `FallbackCacheManager` 在 Redis 不可用时自动降级到纯 Caffeine
- **缓存命名** — `publicGallery`、`pictureSearch`、`adminPending`、`moderationHistory`、`tagCatalog`
- **缓存失效** — 写操作自动驱逐相关缓存，保证数据一致性

### 🗄️ 存储抽象
- **统一接口** — `StorageService` 接口屏蔽底层差异，业务代码零修改切换存储后端
- **本地存储** — 开发/测试环境使用，文件写入 `data/uploads/`
- **腾讯云 COS** — 生产环境推荐，支持多区域、自定义 Endpoint 和 CDN 前缀
- **切换方式** — 仅需修改 `app.storage.provider` 配置项

---

## 技术栈

### 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 编程语言 |
| Spring Boot | 4.0.1 | Web 应用框架 |
| Spring Security | 6.x | 安全认证框架 |
| Spring Data JPA | 4.x | ORM 持久化 |
| Flyway | 11.x | 数据库版本管理 |

### 中间件与存储

| 组件 | 用途 |
|------|------|
| PostgreSQL | 生产数据库 |
| H2 | 开发/测试内存数据库 |
| Redis | L2 分布式缓存 |
| Caffeine | L1 本地缓存 |
| 腾讯云 COS | 对象存储（可选） |

### 安全与认证

| 组件 | 说明 |
|------|------|
| JJWT 0.12.5 | JWT 令牌生成与验证（HMAC-SHA） |
| Spring Security | 请求鉴权、角色隔离 |

### 可观测性

| 工具 | 功能 |
|------|------|
| Spring Actuator | 健康检查、缓存状态端点 |
| Micrometer + Prometheus | 指标采集与监控 |

---

## 快速开始

### 前置需求

- JDK 21+
- Maven 3.8+
- Redis（可选，不可用时自动降级）
- PostgreSQL（生产环境）/ H2（开发默认）

### 本地开发启动

```bash
# 1. 克隆仓库
git clone https://github.com/dengxingzhi000/cloud-picture-platform.git
cd cloud-picture-platform

# 2. 启动（默认使用 H2 内存数据库，无需额外配置）
./mvnw spring-boot:run

# 3. 验证启动
curl http://localhost:8080/actuator/health
```

### 注册与登录

```bash
# 注册用户（自动创建个人空间）
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@example.com","password":"Demo@1234"}'

# 登录获取 JWT 令牌
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"Demo@1234"}'
```

### 上传图片

```bash
curl -X POST http://localhost:8080/api/pictures \
  -H "Authorization: Bearer <your-jwt-token>" \
  -F "file=@/path/to/image.jpg" \
  -F "visibility=PUBLIC"
```

### 切换生产数据库（PostgreSQL）

在 `application.yml` 中修改：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cloud_picture
    username: your_user
    password: your_password
    driver-class-name: org.postgresql.Driver
```

### 启用腾讯云 COS 存储

```yaml
app:
  storage:
    provider: cos
    cos:
      secret-id: ${COS_SECRET_ID}
      secret-key: ${COS_SECRET_KEY}
      region: ap-guangzhou
      bucket: your-bucket-name
```

### 关键配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `app.storage.provider` | `local` | `local` / `cos` |
| `app.security.jwt.secret` | 占位符 | **生产必须覆盖** |
| `COS_SECRET_ID` | — | COS 环境变量 |
| `COS_SECRET_KEY` | — | COS 环境变量 |
| `spring.data.redis.host` | `192.168.18.145` | 按环境调整 |
| `spring.servlet.multipart.max-file-size` | `50MB` | 单文件上传上限 |

---

## 项目结构

```
cloud-picture-platform/
├── src/main/java/com/cn/cloudpictureplatform/
│   ├── domain/                    # 领域层：实体与枚举（DDD核心）
│   │   ├── user/                  # AppUser, UserRole, UserStatus
│   │   ├── picture/               # PictureAsset, Tag, PictureTag, Visibility, ReviewStatus
│   │   ├── space/                 # Space, SpaceType
│   │   ├── team/                  # Team, TeamMember, TeamMemberEvent, TeamRole
│   │   ├── audit/                 # ModerationRecord
│   │   ├── search/                # PictureSearchDocument
│   │   ├── storage/               # StorageService 接口, StorageResult
│   │   └── ai/                    # AI 域（预留）
│   │
│   ├── application/               # 应用层：用例服务（业务编排）
│   │   ├── auth/                  # AuthService（注册/登录/JWT签发）
│   │   ├── picture/               # PictureService（上传/搜索/审核）
│   │   ├── tag/                   # TagService（标签目录管理）
│   │   ├── team/                  # TeamService（团队/成员/邀请）
│   │   └── search/                # SearchIndexService + SearchMaintenanceService
│   │
│   ├── infrastructure/            # 基础设施层：技术实现
│   │   ├── persistence/           # JPA Repository（10个）
│   │   ├── security/              # JWT过滤器, UserDetails, Principal
│   │   └── storage/               # LocalStorageService, CosStorageService
│   │
│   ├── interfaces/                # 接口层：REST控制器与DTO
│   │   ├── auth/                  # AuthController
│   │   ├── picture/               # PictureController
│   │   ├── tag/                   # TagController
│   │   ├── team/                  # TeamController
│   │   └── admin/                 # AdminPictureController, SearchAdminController
│   │
│   ├── common/                    # 公共工具
│   │   ├── model/                 # BaseEntity（UUID主键 + 审计时间戳）
│   │   ├── web/                   # ApiResponse, PageResponse, ApiErrorCode
│   │   └── exception/             # ApiException, GlobalExceptionHandler
│   │
│   └── config/                    # Spring 配置
│       ├── SecurityConfig.java    # JWT安全策略
│       ├── CacheConfig.java       # 两级缓存配置
│       ├── cache/                 # FallbackCache, FallbackCacheManager
│       └── StorageProperties.java # 存储提供商配置
│
├── src/main/resources/
│   ├── application.yml            # 应用配置
│   └── db/migration/              # Flyway 迁移脚本（V1~V9）
│
└── docs/
    ├── requirements.md            # 需求文档
    ├── plan.md                    # 项目路线图
    └── er.md                      # ER 数据模型图
```

---

## 数据库迁移

项目使用 Flyway 进行数据库版本管理，启动时自动执行。

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__init.sql` | 用户、空间、图片资产核心表 |
| V2 | `V2__moderation_record.sql` | 内容审核记录表 |
| V3 | `V3__picture_tags.sql` | 图片标签关联表（含置信度） |
| V4 | `V4__team_space.sql` | 团队与成员表 |
| V5 | `V5__user_avatar_url.sql` | 用户头像字段 |
| V6 | `V6__team_member_events.sql` | 成员事件审计表 |
| V7 | `V7__team_member_event_detail.sql` | 事件详情字段 |
| V8 | `V8__tag_catalog.sql` | 全局标签目录表 |
| V9 | `V9__picture_search_document.sql` | 搜索文档索引表 |

> **注意：** 永远不要修改已提交的迁移脚本。新的 Schema 变更请创建 `V10__xxx.sql`。

---

## 路线图

| 阶段 | 状态 | 内容 |
|------|------|------|
| **P1** 身份、存储与上传 | ✅ 已完成 | JWT 认证、上传管道、公开画廊、内容审核、团队协作基础 |
| **P2** 元数据与高级搜索 | 🔵 规划中 | EXIF 提取、文件夹管理、全文搜索、批量操作 |
| **P3** 空间与团队扩展 | 🔵 规划中 | 配额生命周期、共享相册、版本历史、评论 |
| **P4** 实时协作 | 🔵 规划中 | WebSocket 在线状态、编辑锁、实时通知 |
| **P5** 分析与 AI | 🔵 规划中 | 存储用量统计、AI 自动标签、相似图片聚类 |

---

## 贡献指南

我们欢迎任何形式的贡献！

### 提交流程

1. Fork 项目
2. 创建特性分支（`git checkout -b feat/your-feature-#issue`）
3. 提交更改（遵循[约定式提交](https://www.conventionalcommits.org/zh-hans/)，使用中文）
4. 推送到分支（`git push origin feat/your-feature-#issue`）
5. 创建 Pull Request

### Commit 格式

```
feat(picture.service): 添加图片相似度搜索功能

- 实现基于向量的相似度计算
- 添加相关 REST API 端点
- 补充单元测试

Closes #42
```

### 代码规范

- Java 21，Spring Boot 4，Lombok
- 4 空格缩进，类名 `PascalCase`，DTO 以 `Request` / `Response` 结尾
- DDD 分层：业务逻辑只进 `application/`，`domain/` 不依赖 Spring
- Schema 变更必须新增 Flyway 迁移脚本，禁止修改已有迁移
- 新功能需要配套单元测试（JUnit 5 + Spring Boot Test）

### 常用命令

```bash
./mvnw test                      # 运行所有测试
./mvnw test -Dtest=TagServiceTests  # 运行单个测试类
./mvnw compile                   # 编译检查
./mvnw checkstyle:check          # 代码风格检查
```

---

## 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

---

## 联系方式

- 📧 Email: [dengxingzhi2015@gmail.com](mailto:dengxingzhi2015@gmail.com)
- 🐛 Issue: [提交问题](https://github.com/dengxingzhi000/cloud-picture-platform/issues)
- 💬 Discussion: [参与讨论](https://github.com/dengxingzhi000/cloud-picture-platform/discussions)

---

<div align="center">

**Made with ❤️ by [dengxingzhi](https://github.com/dengxingzhi000)**

如果项目对你有帮助，请给个 Star ⭐ 支持一下！

</div>