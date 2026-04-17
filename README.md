# CloudPicturePlatform - 云图片协作平台

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/dengxingzhi000/cloud-picture-platform?style=flat-square&color=green)](https://github.com/dengxingzhi000/cloud-picture-platform)
[![GitHub Forks](https://img.shields.io/github/forks/dengxingzhi000/cloud-picture-platform?style=flat-square&color=blue)](https://github.com/dengxingzhi000/cloud-picture-platform)
[![License](https://img.shields.io/badge/license-Apache%202.0-red?style=flat-square)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21-brightgreen?style=flat-square)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/SpringBoot-4.0.1-orange?style=flat-square)](https://spring.io/projects/spring-boot)

基于 Spring Boot 4 + Java 21 的企业级云图片协作平台，面向公开画廊、个人图库、团队空间与实时协同场景。

[简介](#简介) • [当前进度](#当前进度) • [核心能力](#核心能力) • [快速开始](#快速开始) • [项目结构](#项目结构) • [开发路线](#开发路线) • [贡献指南](#贡献指南)

</div>

---

## 简介

**CloudPicturePlatform** 是一个基于 Spring Boot 4.0.1 + Java 21 构建的图片管理与协作平台，采用 DDD 分层架构，当前后端已经具备以下主干能力：

- JWT 鉴权与管理端权限隔离
- 图片上传、公开画廊、审核流
- 标签目录、图片标签、多条件搜索
- 团队空间、邀请流、成员事件审计
- WebSocket 在线协同基础能力
- 本地存储与腾讯云 COS 双存储后端

项目当前重点已经从“搭骨架”转向“闭合团队协作 MVP 链路”，即让团队上传、共享浏览、实时在线协同、通知和搜索治理形成完整可用的产品流。

---

## 当前进度

### 已落地

- 身份认证：注册、登录、当前用户信息、平台角色隔离
- 图片能力：上传、元数据提取、可见性控制、个人空间与团队空间存储
- 审核能力：公开图片待审、审批、审核历史、CSV 导出
- 标签搜索：全局标签目录、图片标签增删查、多维度搜索过滤
- 团队能力：创建团队、邀请/接受/拒绝/取消、角色变更、成员移除、事件审计
- 实时协同基础：STOMP 接入、在线状态、编辑锁、断线清理

### 部分完成

- 搜索索引维护：已有索引表和服务入口，但运维化重建与失败治理仍需补全
- 协同编辑：后端协议和状态管理已具备，前端联调和锁超时策略仍待完善
- 通知能力：基础发布器已存在，但关键业务事件闭环还未完全打通

### 当前优先级

1. 团队空间图片协同 MVP 闭环
2. 搜索索引治理与管理能力
3. 自动化测试与交付质量
4. 团队资产治理、分析和 AI 扩展

相关规划文档：

- [需求说明](docs/requirements.md)
- [项目规划](docs/plan.md)
- [协作里程碑](docs/milestones.md)

---

## 核心能力

### 身份与访问控制

- JWT 无状态认证
- Spring Security 鉴权链路
- 平台角色 `USER / ADMIN`
- 管理端接口隔离为 `/api/admin/**`
- WebSocket JWT 握手鉴权

### 图片资产管理

- 图片上传与存储抽象
- 公开、私有、团队三种可见性
- MD5 校验、尺寸提取、基础元数据持久化
- 个人空间默认上传与团队空间定向上传

### 标签与搜索

- 全局标签目录管理
- 图片标签关联与 AI 标签字段预留
- 关键词、标签、空间、审核状态、拥有者、时间范围、尺寸和方向过滤
- `PictureSearchDocument` 搜索文档索引模型

### 团队协作

- 团队创建自动绑定团队空间
- 邀请工作流与角色管理
- 团队成员事件日志和导出
- 团队场景下的图片访问控制

### 实时协同

- STOMP WebSocket 接入
- 图片协同会话加入/离开
- Presence 在线用户快照
- 图片编辑锁申请与释放
- 断线自动清理协同状态

### 审核与治理

- 公开资源审核状态流转
- 审核记录持久化
- 审核历史分页查询
- 审核历史 CSV 导出

### 存储与缓存

- `StorageService` 屏蔽本地/COS 差异
- Caffeine + Redis 两级缓存
- Redis 不可用时自动降级
- 面向公开画廊、搜索、待审列表等热点读场景缓存

---

## 技术栈

| 技术 | 版本/说明 |
|------|------|
| Java | 21 |
| Spring Boot | 4.0.1 |
| Spring Security | 认证与授权 |
| Spring Data JPA | 持久化 |
| Flyway | 数据库迁移 |
| H2 | 默认开发数据库 |
| PostgreSQL | 生产数据库候选 |
| Redis + Caffeine | 两级缓存 |
| STOMP WebSocket | 实时协同 |
| 腾讯云 COS | 可选对象存储 |

---

## 快速开始

### 前置要求

- JDK 21+
- Maven 3.8+
- Redis 可选
- PostgreSQL 可选，默认可直接使用 H2

### 启动项目

```bash
git clone https://github.com/dengxingzhi000/cloud-picture-platform.git
cd cloud-picture-platform
./mvnw spring-boot:run
```

启动后可检查：

```bash
curl http://localhost:8080/actuator/health
```

### 注册与登录

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@example.com","password":"Demo@1234"}'

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

### 切换 PostgreSQL

在 `src/main/resources/application.yml` 中修改：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cloud_picture
    username: your_user
    password: your_password
    driver-class-name: org.postgresql.Driver
```

### 启用 COS

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

### 常用命令

```bash
./mvnw clean package
./mvnw spring-boot:run
./mvnw test
```

---

## 项目结构

```text
src/main/java/com/cn/cloudpictureplatform
├── application      # 用例服务与业务编排
├── common           # 公共响应、异常、基础模型
├── config           # Spring 配置、缓存、安全、存储配置
├── domain           # 领域实体与枚举
├── infrastructure   # 持久化、安全、搜索、存储实现
├── interfaces       # REST 控制器与 DTO
└── websocket        # 实时协同与通知消息处理
```

```text
src/main/resources
├── application.yml
└── db/migration     # Flyway V1 ~ V9
```

```text
docs
├── requirements.md
├── plan.md
└── milestones.md
```

---

## 数据库迁移

当前已存在的主要 Flyway 迁移：

- `V1__init.sql`：用户、空间、图片资产核心表
- `V2__moderation_record.sql`：审核记录表
- `V3__picture_tags.sql`：图片标签关联表
- `V4__team_space.sql`：团队与成员表
- `V5__user_avatar_url.sql`：用户头像字段
- `V6__team_member_events.sql`：团队成员事件表
- `V7__team_member_event_detail.sql`：成员事件详情字段
- `V8__tag_catalog.sql`：全局标签目录表
- `V9__picture_search_document.sql`：搜索文档表

约束：

- 不修改已提交迁移
- 新 schema 变更使用新版本脚本，例如 `V10__xxx.sql`

---

## 开发路线

### 当前开发主线

- 主线 A：团队空间协同 MVP
- 主线 B：搜索索引治理与管理端维护
- 主线 C：测试补齐与交付质量

### 后续扩展方向

- 团队配额、资产治理、相册/文件夹抽象
- 通知中心与活动流
- 分析报表与管理看板
- AI 自动打标、相似图片聚类、视觉搜索

完整规划见：

- [docs/plan.md](docs/plan.md)
- [docs/milestones.md](docs/milestones.md)

---

## 贡献指南

### 基本约束

- Java 21
- Spring Boot 4
- 4 空格缩进
- DTO 以 `Request` / `Response` 结尾
- 新增实体或表时必须补 Flyway 迁移
- 新功能应补充 JUnit 5 / Spring Boot Test 测试

### 推荐提交格式

```text
feat(team): add invite history export
fix(search): ensure tag filter respects visibility constraints
docs(plan): update collaboration roadmap
```

### 提交流程

1. Fork 项目
2. 创建分支
3. 提交修改
4. 推送分支
5. 发起 Pull Request

---

## 许可证

本项目采用 Apache License 2.0，详见 `LICENSE`。

---

## 联系方式

- Email: [dengxingzhi2015@gmail.com](mailto:dengxingzhi2015@gmail.com)
- Issue: [提交问题](https://github.com/dengxingzhi000/cloud-picture-platform/issues)
- Discussion: [参与讨论](https://github.com/dengxingzhi000/cloud-picture-platform/discussions)
