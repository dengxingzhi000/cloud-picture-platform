# CloudPicturePlatform

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/dengxingzhi000/cloud-picture-platform?style=flat-square&color=green&logo=github&logoColor=white)](https://github.com/dengxingzhi000/cloud-picture-platform)
[![GitHub Forks](https://img.shields.io/github/forks/dengxingzhi000/cloud-picture-platform?style=flat-square&color=blue&logo=github&logoColor=white)](https://github.com/dengxingzhi000/cloud-picture-platform)
[![License](https://img.shields.io/badge/license-Apache%202.0-red?style=flat-square)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21-brightgreen?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/SpringBoot-4.0.5-orange?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-blue?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![CI](https://img.shields.io/github/actions/workflow/status/dengxingzhi000/cloud-picture-platform/ci.yml?style=flat-square&label=ci&logo=githubactions&logoColor=white)](https://github.com/dengxingzhi000/cloud-picture-platform/actions)

**English** · [简体中文](#简介)

Enterprise-grade cloud picture collaboration platform — gallery, team workspace, real-time editing, and AI-powered tagging.

[简介](#简介) · [Features](#features) · [Quick Start](#quick-start) · [Configuration](#configuration) · [Project Structure](#project-structure) · [Contributing](#contributing)

</div>

---

> **Spring Boot 4 + Java 21.** DDD layered architecture. PostgreSQL primary, H2 for local dev. Redis + Caffeine two-tier cache with silent degradation. Local filesystem and Tencent COS dual storage backends.

---

## Features

- **Auth & RBAC.** JWT stateless auth, Spring Security chain, `USER / ADMIN` platform roles, admin endpoints isolated at `/api/admin/**`.
- **Picture Management.** Upload with MD5 dedup, SHA-256 reference counting, public/private/team visibility, metadata extraction.
- **Tags & Search.** Global tag catalog, multi-dimension filtering (keyword, tag, space, audit status, owner, time range, size, orientation).
- **Team Collaboration.** Team creation with auto-bound workspace, invite workflow, role management, member event audit log.
- **Real-time Editing.** STOMP over SockJS at `/ws`, presence snapshots, edit locks, disconnect auto-cleanup, JWT handshake auth.
- **Audit & Moderation.** Public resource audit state machine, audit history with pagination and CSV export.
- **AI Platform.** Python FastAPI + CLIP service communicating via RabbitMQ — auto-tagging, content moderation, visual search.
- **Storage Abstraction.** `StorageService` interface hides local/COS differences. `FallbackCache` wraps Redis (primary) + Caffeine (fallback).

---

## Quick Start

### Prerequisites

- JDK 21+ (GraalVM recommended)
- Maven 3.8+
- PostgreSQL (optional — H2 available via `--spring.profiles.active=dev`)
- Redis (optional — cache silently degrades without it)

### Run

```bash
git clone https://github.com/dengxingzhi000/cloud-picture-platform.git
cd cloud-picture-platform
./mvnw spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

### Register & Login

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@example.com","password":"Demo@1234"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"Demo@1234"}'
```

### Upload a Picture

```bash
curl -X POST http://localhost:8080/api/pictures \
  -H "Authorization: Bearer <your-jwt-token>" \
  -F "file=@/path/to/image.jpg" \
  -F "visibility=PUBLIC"
```

### Common Commands

```bash
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"   # Windows PowerShell

./mvnw compile           # compile only
./mvnw test              # run all tests
./mvnw clean package     # build JAR
./mvnw spring-boot:run   # start on http://localhost:8080
```

---

## Configuration

`src/main/resources/application.yml` is the single config file. Key prefixes:

| Prefix | Description |
|--------|-------------|
| `app.storage.*` | Storage provider (`local` / `cos`) |
| `app.security.jwt.*` | JWT config (change secret for production) |
| `app.bootstrap.admin.*` | Admin auto-creation toggle |
| `app.websocket.*` / `app.collaboration.*` | WebSocket and collab room config |

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `cloud_picture` |
| `DB_USERNAME` | Database user | — |
| `DB_PASSWORD` | Database password | — |
| `REDIS_HOST` | Redis host | `192.168.80.132` |
| `JWT_SECRET` | JWT signing secret | — |

### Switch to PostgreSQL

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cloud_picture
    username: your_user
    password: your_password
    driver-class-name: org.postgresql.Driver
```

### Enable Tencent COS

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

---

## Project Structure

```text
src/main/java/com/cn/cloudpictureplatform/
├── common/          — BaseEntity, ApiException, ApiErrorCode, ApiResponse, GlobalExceptionHandler
├── config/          — Spring @Configuration, properties records, FallbackCache
├── domain/          — JPA entities (all extend BaseEntity with UUID v7 IDs)
├── application/     — Use-case services (transactional boundaries)
├── infrastructure/  — Persistence, security (JWT), storage (local/COS), search
├── interfaces/      — REST controllers + DTOs (*Request/*Response)
└── websocket/       — STOMP/SockJS real-time: collab editing, presence, notifications

ai-platform/         — Python FastAPI + CLIP service (RabbitMQ)
src/main/resources/db/migration/  — Flyway migrations (V1 ~ V36)
```

---

## Database Migrations

Flyway in `src/main/resources/db/migration/`. Current scripts:

| Script | Scope |
|--------|-------|
| `V1__init.sql` | Core schema (users, spaces, pictures, tags, teams, audit, search, collab) |
| `V2__rbac.sql` | RBAC permission tables |
| `V13__rbac_management.sql` | RBAC audit log, system markers |
| `V14__*.sql` ~ `V36__*.sql` | Incremental migrations (indexes, AI chat, observability) |

> When adding entities: create a Flyway migration AND add JPA annotations. Both are needed.

---

## Documentation

- [Requirements](docs/requirements.md) — feature requirements
- [Plan](docs/plan.md) — project roadmap
- [Milestones](docs/milestones.md) — collaboration milestones

---

## Contributing

### Conventions

- Java 21, Spring Boot 4
- 4 spaces, no tabs
- Lombok everywhere (`@RequiredArgsConstructor` for constructor injection, no `@Autowired`)
- DTOs named `*Request` / `*Response`
- New entities require Flyway migration
- Transactional boundaries on `application/` service methods
- `@Modifying @Query` for atomic updates — never read-modify-write

### Commit Format

```
feat(team): add invite history export
fix(search): ensure tag filter respects visibility constraints
docs(plan): update collaboration roadmap
```

### Workflow

1. Fork the repo
2. Create a feature branch
3. Commit changes
4. Push and open a Pull Request

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

---

## Contact

- Email: [dengxingzhi2015@gmail.com](mailto:dengxingzhi2015@gmail.com)
- Issues: [github.com/dengxingzhi000/cloud-picture-platform/issues](https://github.com/dengxingzhi000/cloud-picture-platform/issues)
- Discussions: [github.com/dengxingzhi000/cloud-picture-platform/discussions](https://github.com/dengxingzhi000/cloud-picture-platform/discussions)
