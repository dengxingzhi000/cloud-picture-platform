# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/com/cn/cloudpictureplatform`: backend source code.
  - `common`, `config`: shared utilities and Spring configuration.
  - `domain`: DDD entities and enums (user, picture, space, audit).
  - `application`: use-case services (auth, picture workflows).
  - `infrastructure`: persistence, storage, security integrations.
  - `interfaces`: REST controllers and DTOs.
- `src/main/resources`: `application.yml` and Flyway migrations under `db/migration`.
- `src/test/java`: JUnit 5 tests.
- `docs`: architecture notes, plan, and ER diagram (`docs/er.md`).

## Build, Test, and Development Commands
- `./mvnw clean package` builds the application JAR.
- `./mvnw spring-boot:run` runs the backend on `http://localhost:8080`.
- `./mvnw test` runs all tests.

## Coding Style & Naming Conventions
- Java 21 with Spring Boot 4; use Lombok for data classes.
- Indentation: 4 spaces, no tabs.
- Packages are lowercase; classes are `PascalCase`.
- DTOs end with `Request` / `Response` (e.g., `ReviewRequest`).
- Enums use `UPPER_SNAKE` values (e.g., `APPROVED`).

## Testing Guidelines
- Framework: JUnit 5 + Spring Boot Test.
- Tests live in `src/test/java` and should be named `*Tests`.
- When adding entities or tables, add a Flyway migration and update tests to run against the migrated schema.

## Commit & Pull Request Guidelines
- No established commit convention yet. Prefer a short, imperative summary:
  - Example: `feat(auth): add JWT login endpoint`
- PRs should include:
  - Summary of changes and affected modules.
  - Migration notes (new Flyway scripts, schema changes).
  - Config changes (e.g., new `application.yml` keys).
  - Screenshots for UI changes (if applicable).

## Security & Configuration Tips
- Update `app.security.jwt.secret` in `src/main/resources/application.yml` for any real deployment.
- Storage provider is `local` by default; set `app.storage.provider=cos` and fill `app.storage.cos.*` to use COS.
- Default DB is in-memory H2. Switch to Postgres by changing `spring.datasource.*`.
- Redis host is configured in `application.yml`; adjust for your environment or disable caching if unavailable.
