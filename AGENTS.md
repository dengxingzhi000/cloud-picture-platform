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

# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Cursor, Copilot, Antigravity, etc.) when working with code in this repository.

## Repository Overview

A collection of skills for Claude.ai and Claude Code for senior software engineers. Skills are packaged instructions and scripts that extend Claude and your coding agents capabilities.

## OpenCode Integration

OpenCode uses a **skill-driven execution model** powered by the `skill` tool and this repository's `/skills` directory.

### Core Rules

- If a task matches a skill, you MUST invoke it
- Skills are located in `skills/<skill-name>/SKILL.md`
- Never implement directly if a skill applies
- Always follow the skill instructions exactly (do not partially apply them)

### Intent → Skill Mapping

The agent should automatically map user intent to skills:

- Feature / new functionality → `spec-driven-development`, then `incremental-implementation`, `test-driven-development`
- Planning / breakdown → `planning-and-task-breakdown`
- Bug / failure / unexpected behavior → `debugging-and-error-recovery`
- Code review → `code-review-and-quality`
- Refactoring / simplification → `code-simplification`
- API or interface design → `api-and-interface-design`
- UI work → `frontend-ui-engineering`

### Lifecycle Mapping (Implicit Commands)

OpenCode does not support slash commands like `/spec` or `/plan`.

Instead, the agent must internally follow this lifecycle:

- DEFINE → `spec-driven-development`
- PLAN → `planning-and-task-breakdown`
- BUILD → `incremental-implementation` + `test-driven-development`
- VERIFY → `debugging-and-error-recovery`
- REVIEW → `code-review-and-quality`
- SHIP → `shipping-and-launch`

### Execution Model

For every request:

1. Determine if any skill applies (even 1% chance)
2. Invoke the appropriate skill using the `skill` tool
3. Follow the skill workflow strictly
4. Only proceed to implementation after required steps (spec, plan, etc.) are complete

### Anti-Rationalization

The following thoughts are incorrect and must be ignored:

- "This is too small for a skill"
- "I can just quickly implement this"
- "I’ll gather context first"

Correct behavior:

- Always check for and use skills first

This ensures OpenCode behaves similarly to Claude Code with full workflow enforcement.

## Orchestration: Personas, Skills, and Commands

This repo has three composable layers. They have different jobs and should not be confused:

- **Skills** (`skills/<name>/SKILL.md`) — workflows with steps and exit criteria. The *how*. Mandatory hops when an intent matches.
- **Personas** (`agents/<role>.md`) — roles with a perspective and an output format. The *who*.
- **Slash commands** (`.claude/commands/*.md`) — user-facing entry points. The *when*. The orchestration layer.

Composition rule: **the user (or a slash command) is the orchestrator. Personas do not invoke other personas.** A persona may invoke skills.

The only multi-persona orchestration pattern this repo endorses is **parallel fan-out with a merge step** — used by `/ship` to run `code-reviewer`, `security-auditor`, and `test-engineer` concurrently and synthesize their reports. Do not build a "router" persona that decides which other persona to call; that's the job of slash commands and intent mapping.

See [agents/README.md](agents/README.md) for the decision matrix and [references/orchestration-patterns.md](references/orchestration-patterns.md) for the full pattern catalog.

**Claude Code interop:** the personas in `agents/` work as Claude Code subagents (auto-discovered from this plugin's `agents/` directory) and as Agent Teams teammates (referenced by name when spawning). Two platform constraints align with our rules: subagents cannot spawn other subagents, and teams cannot nest. Plugin agents silently ignore the `hooks`, `mcpServers`, and `permissionMode` frontmatter fields.

## Creating a New Skill

### Directory Structure

```
skills/
  {skill-name}/           # kebab-case directory name
    SKILL.md              # Required: skill definition
    scripts/              # Required: executable scripts
      {script-name}.sh    # Bash scripts (preferred)
  {skill-name}.zip        # Required: packaged for distribution
```

### Naming Conventions

- **Skill directory**: `kebab-case` (e.g. `web-quality`)
- **SKILL.md**: Always uppercase, always this exact filename
- **Scripts**: `kebab-case.sh` (e.g., `deploy.sh`, `fetch-logs.sh`)
- **Zip file**: Must match directory name exactly: `{skill-name}.zip`

### SKILL.md Format

```markdown
---
name: {skill-name}
description: {One sentence describing what the skill does, followed by one or more "Use when" trigger conditions. Include trigger phrases like "Deploy my app" or "Check logs" when helpful.}
---

# {Skill Title}

{Brief overview of what the skill does and why it matters.}

## How It Works

{Numbered list explaining the skill's workflow}

Equivalent headings like `Workflow`, `Core Process`, or `When to Use` are fine when they communicate the same structure clearly.

## Usage (Optional)

Include this section only if the skill ships runnable helpers under `scripts/`. Markdown-only skills can omit both the section and the directory entirely.

```bash
bash /mnt/skills/user/{skill-name}/scripts/{script}.sh [args]
```

**Arguments:**
- `arg1` - Description (defaults to X)

**Examples:**
{Show 2-3 common usage patterns}

## Output

{Show example output users will see}

## Present Results to User

{Template for how Claude should format results when presenting to users}

## Troubleshooting

{Common issues and solutions, especially network/permissions errors}
```

### Best Practices for Context Efficiency

Skills are loaded on-demand — only the skill name and description are loaded at startup. The full `SKILL.md` loads into context only when the agent decides the skill is relevant. To minimize context usage:

- **Keep SKILL.md under 500 lines** — put detailed reference material in separate files
- **Write specific descriptions** — helps the agent know exactly when to activate the skill
- **Use progressive disclosure** — reference supporting files that get read only when needed
- **Prefer scripts over inline code** — script execution doesn't consume context (only output does)
- **File references work one level deep** — link directly from SKILL.md to supporting files

### Script Requirements

- Use `#!/bin/bash` shebang
- Use `set -e` for fail-fast behavior
- Write status messages to stderr: `echo "Message" >&2`
- Write machine-readable output (JSON) to stdout
- Include a cleanup trap for temp files
- Reference the script path as `/mnt/skills/user/{skill-name}/scripts/{script}.sh`

### Creating the Zip Package

After creating or updating a skill:

```bash
cd skills
zip -r {skill-name}.zip {skill-name}/
```

### End-User Installation

Document these two installation methods for users:

**Claude Code:**
```bash
cp -r skills/{skill-name} ~/.claude/skills/
```

**claude.ai:**
Add the skill to project knowledge or paste SKILL.md contents into the conversation.

If the skill requires network access, instruct users to add required domains at `claude.ai/settings/capabilities`.

