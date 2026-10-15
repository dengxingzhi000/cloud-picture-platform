<div align="center">

# CloudPicturePlatform

### Enterprise-grade cloud picture collaboration — gallery, team workspace, real-time editing, and AI tagging.

**Spring Boot 4 + Java 21, DDD layered. PostgreSQL with an H2 dev profile,
two-tier Redis + Caffeine cache that degrades silently, and pluggable
local-filesystem or Tencent COS storage.**

[![Build](https://img.shields.io/github/actions/workflow/status/dengxingzhi000/cloud-picture-platform/ci.yml?style=flat-square&label=ci&logo=githubactions&logoColor=white)](https://github.com/dengxingzhi000/cloud-picture-platform/actions)
[![Stars](https://img.shields.io/github/stars/dengxingzhi000/cloud-picture-platform?color=ffcb47&labelColor=black&style=flat-square&logo=github)](https://github.com/dengxingzhi000/cloud-picture-platform/stargazers)
[![Forks](https://img.shields.io/github/forks/dengxingzhi000/cloud-picture-platform?color=8ae8ff&labelColor=black&style=flat-square&logo=github)](https://github.com/dengxingzhi000/cloud-picture-platform/network/members)
[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white&style=flat-square)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/SpringBoot-4.0.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](#license)

[Quick start](#quick-start) · [Capabilities](#capabilities) · [Configuration](#configuration) · [Structure](#project-structure) · [Migrations](#database-migrations) · [Contributing](#contributing)

<code>./mvnw spring-boot:run</code>

</div>

> [!IMPORTANT]
> **`REDIS_HOST` currently defaults to `192.168.80.132`** — a private LAN address
> left in `application.yml`. It is harmless where the cache silently degrades, but
> it leaks internal network topology and will produce confusing connection timeouts
> anywhere else. Override it, or change the default to `localhost`.

---

Managing a shared picture library is mostly a consistency problem: two people
uploading the same file, a picture being deleted while three teams reference it,
a public picture that needs review before anyone sees it. This platform treats
those as the interesting cases rather than the exceptions.

CloudPicturePlatform is a Spring Boot 4 backend organized as DDD layers
(`domain` → `application` → `infrastructure` → `interfaces`). Deduplication is
content-addressed by MD5, reference counting by SHA-256, visibility is
public/private/team, and public content moves through an audit state machine with
history and CSV export. Real-time collaboration runs over STOMP/SockJS at `/ws`
with presence snapshots, edit locks, and disconnect cleanup.

Two integrations worth calling out: a **Python FastAPI + CLIP service** reached
over RabbitMQ for auto-tagging, moderation, and visual search; and a
**`FallbackCache`** that wraps Redis as primary with Caffeine as fallback, so the
platform stays up when Redis does not.

## Quick start

### Prerequisites

- **JDK 21+** (GraalVM recommended)
- **Maven 3.8+** (a wrapper `./mvnw` is included)
- **PostgreSQL** — optional, H2 is available via `--spring.profiles.active=dev`
- **Redis** — optional, the cache degrades silently without it

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

### Register & login

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@example.com","password":"Demo@1234"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"Demo@1234"}'
```

### Upload a picture

```bash
curl -X POST http://localhost:8080/api/pictures \
  -H "Authorization: Bearer <your-jwt-token>" \
  -F "file=@/path/to/image.jpg" \
  -F "visibility=PUBLIC"
```

### Common commands

```bash
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"   # Windows PowerShell

./mvnw compile           # compile only
./mvnw test              # run all tests
./mvnw clean package     # build JAR
./mvnw spring-boot:run   # start on http://localhost:8080
```

## Capabilities

| Capability          | What it does                                                                   | Key constraint                                                              |
| ------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| Auth & RBAC         | JWT stateless auth, Spring Security chain, `USER` / `ADMIN` platform roles     | Admin endpoints isolated at `/api/admin/**`                                 |
| Picture management  | Upload with MD5 dedup and SHA-256 reference counting                           | Visibility is public / private / team; metadata extracted on upload         |
| Tags & search       | Global tag catalog with multi-dimension filtering                              | Filter by keyword, tag, space, audit status, owner, time, size, orientation |
| Team collaboration  | Team creation with auto-bound workspace, invite workflow, role management      | Member events written to an audit log                                       |
| Real-time editing   | STOMP over SockJS at `/ws`: presence snapshots, edit locks, disconnect cleanup | JWT authenticated during the WebSocket handshake                            |
| Audit & moderation  | Public-resource audit state machine with paginated history and CSV export      | Public content is unreviewed until it passes audit                          |
| AI platform         | Auto-tagging, content moderation, visual search                                | Separate Python FastAPI + CLIP service over RabbitMQ                        |
| Storage abstraction | `StorageService` hides the local/COS difference                                | Local filesystem or Tencent COS, chosen by `app.storage.provider`           |
| Two-tier cache      | `FallbackCache` wraps Redis (primary) with Caffeine (fallback)                 | Degrades silently — Redis being down does not fail requests                 |

## Configuration

`src/main/resources/application.yml` is the single config file. Key prefixes:

| Prefix                                    | Description                                   |
| ----------------------------------------- | --------------------------------------------- |
| `app.storage.*`                           | Storage provider (`local` / `cos`)            |
| `app.security.jwt.*`                      | JWT config (change the secret for production) |
| `app.bootstrap.admin.*`                   | Admin auto-creation toggle                    |
| `app.websocket.*` / `app.collaboration.*` | WebSocket and collaboration room config       |

### Environment variables

| Variable      | Description        | Default            |
| ------------- | ------------------ | ------------------ |
| `DB_HOST`     | PostgreSQL host    | `localhost`        |
| `DB_PORT`     | PostgreSQL port    | `5432`             |
| `DB_NAME`     | Database name      | `cloud_picture`    |
| `DB_USERNAME` | Database user      | —                  |
| `DB_PASSWORD` | Database password  | —                  |
| `REDIS_HOST`  | Redis host         | `192.168.80.132` ⚠ |
| `JWT_SECRET`  | JWT signing secret | —                  |

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

## Project structure

```text
src/main/java/com/cn/cloudpictureplatform/
├── common/           — BaseEntity, ApiException, ApiErrorCode, ApiResponse, GlobalExceptionHandler
├── config/           — Spring @Configuration, properties records, FallbackCache
├── domain/           — JPA entities (all extend BaseEntity with UUID v7 IDs)
├── application/      — Use-case services (transactional boundaries)
├── infrastructure/   — Persistence, security (JWT), storage (local/COS), search
├── interfaces/       — REST controllers + DTOs (*Request/*Response)
└── websocket/        — STOMP/SockJS real-time: collab editing, presence, notifications

ai-platform/                         — Python FastAPI + CLIP service (RabbitMQ)
src/main/resources/db/migration/     — Flyway migrations (V1 ~ V36)
```

## Database migrations

Flyway in `src/main/resources/db/migration/`. Current scripts:

| Script                      | Scope                                                                     |
| --------------------------- | ------------------------------------------------------------------------- |
| `V1__init.sql`              | Core schema (users, spaces, pictures, tags, teams, audit, search, collab) |
| `V2__rbac.sql`              | RBAC permission tables                                                    |
| `V13__rbac_management.sql`  | RBAC audit log, system markers                                            |
| `V14__*.sql` ~ `V36__*.sql` | Incremental migrations (indexes, AI chat, observability)                  |

> [!WARNING]
> **Adding an entity requires two changes, not one.** Create a Flyway migration
> _and_ add the JPA annotations. Skipping either one silently desynchronizes the
> schema from the mapping.

## Documentation

- [Requirements](docs/requirements.md) — feature requirements
- [Plan](docs/plan.md) — project roadmap
- [Milestones](docs/milestones.md) — collaboration milestones

## Contributing

### Conventions

- Java 21, Spring Boot 4
- 4 spaces, no tabs
- Lombok everywhere (`@RequiredArgsConstructor` for constructor injection, no `@Autowired`)
- DTOs named `*Request` / `*Response`
- New entities require a Flyway migration
- Transactional boundaries on `application/` service methods
- `@Modifying @Query` for atomic updates — never read-modify-write

### Commit format

Commit messages are conventional commits prefixed with an issue number:

```
202: feat(team): add invite history export
fix(search): ensure tag filter respects visibility constraints
docs(plan): update collaboration roadmap
```

### Workflow

1. Fork the repo
2. Create a feature branch
3. Commit changes
4. Push and open a Pull Request

## License

Apache License 2.0.

> [!NOTE]
> The `LICENSE` file is **missing from this repository** — the text above records
> the intended license, but the file needs to be added before publishing.

## Contact

- Email: [dengxingzhi2015@gmail.com](mailto:dengxingzhi2015@gmail.com)
- Issues: [github.com/dengxingzhi000/cloud-picture-platform/issues](https://github.com/dengxingzhi000/cloud-picture-platform/issues)
- Discussions: [github.com/dengxingzhi000/cloud-picture-platform/discussions](https://github.com/dengxingzhi000/cloud-picture-platform/discussions)
