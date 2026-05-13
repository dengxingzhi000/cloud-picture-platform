# Repository Guidelines

## Environment Setup

- **JAVA_HOME is not on PATH.** Set it before running Maven:
  `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"`
- Spring Boot **4.0.5** + Java **21** (GraalVM). Not Spring Boot 3 — API differences exist (e.g. `Jackson2ObjectMapperBuilder` is deprecated; use `JsonMapper.builder()`).
- **PostgreSQL** is the primary database. Flyway migrations use native PG syntax (`timestamptz`, `double precision`, partial indexes).
- **H2** in `MODE=PostgreSQL` available via `--spring.profiles.active=dev` for local development without PG.

## Build, Test, and Development Commands

```powershell
# Set JAVA_HOME first (Windows PowerShell)
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"

.\mvnw compile          # compile only
.\mvnw test             # run all tests
.\mvnw clean package    # build JAR
.\mvnw spring-boot:run  # start on http://localhost:8080
```

- `TeamServiceTests.java` has a pre-existing syntax error (missing class body). Skip it or fix before running full test suite.
- No `typecheck` or `lint` commands — this is a plain Java/Maven project.

## Project Structure

```
src/main/java/com/cn/cloudpictureplatform/
  common/          — BaseEntity, ApiException, ApiErrorCode, ApiResponse, GlobalExceptionHandler
  config/          — Spring @Configuration classes + properties records
  config/cache/    — FallbackCache (Redis primary → Caffeine fallback, tolerates Redis down)
  domain/          — JPA entities (all extend BaseEntity with UUID v7 IDs)
  application/     — Use-case services (transactional boundaries live here)
  infrastructure/  — persistence (repositories), security (JWT), storage (local/COS), search
  interfaces/      — REST controllers + DTOs
  websocket/       — STOMP/SockJS real-time: collab editing, presence, notifications
```

- **Entry point:** `CloudPicturePlatformApplication.java`
- **Repositories** are in `infrastructure/persistence/`, not in `domain/`.
- **Controllers** are in `interfaces/`, one sub-package per bounded area.
- **DTOs** are in `interfaces/*/dto/`, named `*Request` / `*Response`.

## Key Architecture Patterns

- **BaseEntity** (`common/model/`): all entities extend it. Provides `id` (UUID v7 via `@UuidGenerator`), `createdAt`, `updatedAt` (auto-audited).
- **Error handling:** throw `new ApiException(ApiErrorCode.XXX, "message")`. `GlobalExceptionHandler` converts to `ApiResponse`.
- **Cache:** `FallbackCache` wraps Redis (primary) + Caffeine (fallback). If Redis is unreachable, cache operations silently degrade. Cache names: `publicGallery`, `pictureSearch`, `adminPending`, `pictureRecommendations`.
- **Storage:** `app.storage.provider` switches between `local` (filesystem) and `cos` (Tencent COS). Both implement `StorageService`.
- **File deduplication:** `FileDeduplicationService` tracks files by SHA-256 hash with reference counting. `DeduplicationPictureUploadService` uses `findOrCreateFileContent()` with `REQUIRES_NEW` transaction for concurrent-safe dedup.
- **Search:** `DatabaseSearchIndexService` (not Elasticsearch). Async indexing via `searchIndexTaskExecutor` thread pool.
- **WebSocket:** STOMP over SockJS at `/ws`. Auth via `WebSocketAuthChannelInterceptor` (JWT in STOMP headers). Topics: `/topic/admin/*`, `/topic/pictures/{id}/collab`. User queues: `/user/queue/notifications`.
- **Admin bootstrap:** `DataInitializer` creates admin/admin123 on startup when `app.bootstrap.admin.enabled=true`. `/api/admin/**` requires `ROLE_ADMIN`.
- **Public endpoints** (no auth): `/api/auth/register`, `/api/auth/login`, `GET /api/pictures/public`, `GET /api/pictures/search`, `GET /api/pictures/recommendations`, `/ws/**`, `/actuator/health`.

## Database & Migrations

- Flyway migrations in `src/main/resources/db/migration/`, versioned `V1__init.sql` through `V12__*.sql`.
- JPA `ddl-auto: update` + Flyway `enabled: true` — both run on startup. Flyway applies versioned scripts; Hibernate auto-creates any missing columns/tables.
- When adding entities: create a Flyway migration AND add JPA annotations. Both are needed.
- Column types use `columnDefinition = "uuid"` for UUID fields (Postgres/H2 compatible).

## Coding Conventions

- **Lombok everywhere:** `@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor` on entities. `@RequiredArgsConstructor` on services.
- **4 spaces, no tabs.** Packages lowercase, classes PascalCase.
- **Enums:** `@Enumerated(EnumType.STRING)` with `UPPER_SNAKE` values.
- **No `@Autowired`** — constructor injection via `@RequiredArgsConstructor` or explicit constructors.
- **Transactional boundaries** are on `application/` service methods, not on controllers or repositories.
- **`@Modifying @Query`** for atomic updates (e.g. `incrementRefCount`, `incrementUsedBytes`). Never read-modify-write entity fields that need concurrency safety.

## Testing

- JUnit 5 + Spring Boot Test. Test classes named `*Tests`.
- Tests use H2 in-memory (test profile auto-activates). Flyway migrations run automatically.
- `TeamServiceTests.java` is broken — do not use as a reference.

## Configuration

- `application.yml` is the single config file. Key prefixes:
  - `app.storage.*` — storage provider settings
  - `app.security.jwt.*` — JWT config (secret must be changed for production)
  - `app.bootstrap.admin.*` — admin auto-creation toggle
  - `app.websocket.*` / `app.collaboration.*` — WebSocket and collab room config
- PostgreSQL is the default database. Connection configured via `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` env vars.
- For local dev without PG: `--spring.profiles.active=dev` uses H2 in-memory.
- Redis is required for caching. Host: `192.168.80.132:6379` (hardcoded in dev config). Disable or change for your environment.
- Actuator exposes: `health`, `info`, `metrics`, `caches`, `prometheus`.
