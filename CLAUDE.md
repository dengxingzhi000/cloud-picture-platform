# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 4.0.1 / Java 21 cloud picture platform with JWT authentication, team collaboration, tag management, content moderation, and pluggable storage backends (local filesystem or Tencent COS).

## Development Commands

```bash
./mvnw clean package        # Build JAR
./mvnw spring-boot:run      # Run on http://localhost:8080
./mvnw test                 # Run all tests
./mvnw test -Dtest=SomeTests  # Run a single test class
./mvnw compile              # Compile only
./mvnw checkstyle:check     # Lint
```

## Architecture

The codebase follows DDD layering under `com.cn.cloudpictureplatform`:

- **`domain/`** — JPA entities and enums (the core model). No Spring services here.
- **`application/`** — Use-case services that orchestrate domain objects: `AuthService`, `PictureService`, `TagService`, `TeamService`, `SearchIndexService`.
- **`infrastructure/`** — Technical implementations: JPA repositories (`persistence/`), JWT + Spring Security (`security/`), storage adapters (`storage/`).
- **`interfaces/`** — REST controllers and DTOs, grouped by feature: `auth/`, `picture/`, `tag/`, `team/`, `admin/`.
- **`common/`** — Shared `BaseEntity` (UUID PK, audit timestamps), `ApiResponse`/`PageResponse` wrappers, `ApiException`, `GlobalExceptionHandler`.
- **`config/`** — Spring `@Configuration` classes for security, caching, JPA auditing, storage, JWT properties.

### Key Domain Entities

| Entity | Purpose |
|---|---|
| `AppUser` | User account; roles: `USER`, `ADMIN` |
| `Space` | Storage quota container; types: `PERSONAL`, `TEAM` |
| `PictureAsset` | Image metadata + storage key; has `Visibility` and `ReviewStatus` |
| `Tag` / `PictureTag` | Tag catalog; junction table stores confidence score and AI provider |
| `Team` / `TeamMember` | Collaborative workspace; roles: `OWNER`, `ADMIN`, `MEMBER` |
| `TeamMemberEvent` | Audit log for membership changes |
| `ModerationRecord` | Approval/rejection audit trail for public pictures |
| `PictureSearchDocument` | Denormalized search index record per picture |

Registration creates a `PERSONAL` Space automatically. Team creation creates a `TEAM` Space and owner `TeamMember` record.

### Application Services

- **`PictureService`**: upload (checksum, dimension extraction), gallery listing with caching, JPA Specification-based search (tags, visibility, date range, orientation), moderation workflow, tag attachment.
- **`TagService`**: tag catalog CRUD; renaming propagates to all `PictureTag` records; blocks deletion of in-use tags.
- **`TeamService`**: team CRUD, invite/accept/reject workflow, role changes, member event logging.
- **`AuthService`**: register, login (returns JWT), profile update.

### Caching

Two-level: Caffeine (L1) + Redis (L2). Named caches used: `publicGallery`, `pictureSearch`, `adminPending`, `moderationHistory`. `FallbackCacheManager` wraps Redis to degrade gracefully when unavailable.

### Storage Abstraction

`StorageService` interface with two implementations selected by `app.storage.provider`:
- `local` → `LocalStorageService` (writes to `data/uploads/`)
- `cos` → `CosStorageService` (Tencent Object Storage)

### Security

Stateless JWT (JJWT 0.12.5, HMAC-SHA). `JwtAuthenticationFilter` populates the Spring Security context per request. Admin endpoints (`/api/admin/**`) require `ADMIN` role.

### Database

Default: H2 in-memory (dev). Production: PostgreSQL. Schema managed by Flyway (`src/main/resources/db/migration/`, V1–V9). Always add a new `V{n}__description.sql` migration for schema changes — never modify existing migrations.

## Coding Conventions

- Lombok for data classes (`@Data`, `@Builder`, etc.)
- DTOs named `*Request` / `*Response`; enums use `UPPER_SNAKE_CASE`
- 4-space indentation, no tabs
- Commit format: `feat(module): short imperative description`

## Configuration Notes

- JWT secret (`app.security.jwt.secret`) defaults to an insecure placeholder — must be overridden in production.
- Redis host is `192.168.18.145:6379` by default; adjust or disable if unavailable.
- COS credentials are in `app.storage.cos.*`; storage provider switches via `app.storage.provider`.