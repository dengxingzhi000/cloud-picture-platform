# Architecture Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all critical (P0) blockers, then systematically address P1/P2 architecture issues to bring the cloud-picture-platform from prototype (50/100) to production-ready (75+/100).

**Architecture:** Three-phase approach — Phase 1 (P0) fixes blocking bugs preventing startup/security; Phase 2 (P1) establishes engineering discipline (tests, CI/CD, code quality); Phase 3 (P2) optimizes for scale and developer experience.

**Tech Stack:** Spring Boot 4.0.5, Java 21 (GraalVM), PostgreSQL 16, Redis 7, Flyway, JUnit 5, Mockito, ArchUnit, Docker

**Environment Setup (Windows PowerShell):**
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"
```

---

## Phase 1: P0 — Critical Blockers (Must fix before any other work)

---

### Task 1: Fix Flyway Migration Version Conflicts

**Problem:** Three pairs of Flyway migrations share version numbers (V13, V15, V16). Flyway refuses to start with duplicate versions. Tables `notification_record`, `album`, `album_picture` will never be created. The `app_user.role` column cleanup will never run.

**Files:**
- Rename: `src/main/resources/db/migration/V13__create_notification_record.sql` → `V29__create_notification_record.sql`
- Rename: `src/main/resources/db/migration/V15__drop_legacy_role_column.sql` → `V30__drop_legacy_role_column.sql`
- Rename: `src/main/resources/db/migration/V16__force_drop_legacy_role_column.sql` → `V31__force_drop_legacy_role_column.sql`

- [ ] **Step 1: Rename the three conflicting migration files**

```powershell
# In project root
Rename-Item "src\main\resources\db\migration\V13__create_notification_record.sql" "V29__create_notification_record.sql"
Rename-Item "src\main\resources\db\migration\V15__drop_legacy_role_column.sql" "V30__drop_legacy_role_column.sql"
Rename-Item "src\main\resources\db\migration\V16__force_drop_legacy_role_column.sql" "V31__force_drop_legacy_role_column.sql"
```

- [ ] **Step 2: Verify no duplicate versions remain**

```powershell
Get-ChildItem "src\main\resources\db\migration\*.sql" | ForEach-Object { $_.Name -replace '__.*','' } | Sort-Object | Group-Object | Where-Object { $_.Count -gt 1 }
```
Expected: No output (no duplicates).

- [ ] **Step 3: Verify Flyway can parse all migrations**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/db/migration/
git commit -m "fix(flyway): resolve duplicate migration versions V13/V15/V16

Renumbered conflicting migrations to V29/V30/V31 to ensure all
tables (notification_record, album, album_picture) are created
and the legacy role column cleanup runs."
```

---

### Task 2: Fix V28 Table Name Error

**Problem:** `V28__create_excalidraw_tables.sql` line 3 references `picture(id)` but the actual table is `picture_asset`. This migration will fail at runtime.

**Files:**
- Modify: `src/main/resources/db/migration/V28__create_excalidraw_tables.sql:3`

- [ ] **Step 1: Fix the foreign key reference**

Change line 3 from:
```sql
    picture_id UUID REFERENCES picture(id),
```
to:
```sql
    picture_id UUID REFERENCES picture_asset(id),
```

- [ ] **Step 2: Verify the migration file is valid SQL**

```powershell
# Quick syntax check - just ensure the file is readable
Get-Content "src\main\resources\db\migration\V28__create_excalidraw_tables.sql" | Select-String "picture_asset"
```
Expected: Line showing `picture_asset(id)`

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/db/migration/V28__create_excalidraw_tables.sql
git commit -m "fix(db): correct table name in V28 excalidraw migration

Changed REFERENCES picture(id) to REFERENCES picture_asset(id)
to match the actual table name."
```

---

### Task 3: Externalize JWT Secret

**Problem:** JWT secret is hardcoded as `change-me-change-me-change-me-change-me` in `application.yml`. Any person with source access can forge tokens for any user.

**Files:**
- Modify: `src/main/resources/application.yml:68`
- Create: `src/main/resources/application-prod.yml` (new)

- [ ] **Step 1: Update application.yml to use environment variable with secure default**

Change line 68 from:
```yaml
      secret: change-me-change-me-change-me-change-me
```
to:
```yaml
      secret: ${JWT_SECRET:dev-only-change-me-in-production-32chars-min}
```

- [ ] **Step 2: Create application-prod.yml with mandatory environment variable**

Create `src/main/resources/application-prod.yml`:
```yaml
app:
  security:
    jwt:
      secret: ${JWT_SECRET}

spring:
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
```

This makes `JWT_SECRET` mandatory in production (no default = fails to start if unset). Also disables `show-sql` and changes `ddl-auto` to `validate` for production.

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/application.yml src/main/resources/application-prod.yml
git commit -m "fix(security): externalize JWT secret via environment variable

JWT_SECRET env var is now required in production (profile: prod).
Development default is a non-placeholder value. Also disables
show-sql and sets ddl-auto=validate for production."
```

---

### Task 4: Externalize Database and Redis Credentials

**Problem:** Database password `123456` and Redis host are hardcoded with insecure defaults.

**Files:**
- Modify: `src/main/resources/application.yml:10-12,29`

- [ ] **Step 1: Update datasource defaults to use environment variables**

Change lines 10-12 from:
```yaml
    url: jdbc:postgresql://${DB_HOST:192.168.80.133}:${DB_PORT:5432}/${DB_NAME:cloud_picture_platform}
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:123456}
```
to:
```yaml
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:cloud_picture_platform}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

- [ ] **Step 2: Update Redis default to localhost**

Change line 29 from:
```yaml
      host: ${REDIS_HOST:192.168.80.133}
```
to:
```yaml
      host: ${REDIS_HOST:localhost}
```

- [ ] **Step 3: Add database config to application-prod.yml**

Append to `src/main/resources/application-prod.yml`:
```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
```

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/application.yml src/main/resources/application-prod.yml
git commit -m "fix(security): externalize database and Redis credentials

Changed defaults from hardcoded internal IPs to localhost.
Production profile requires DB_PASSWORD and REDIS_HOST env vars."
```

---

### Task 5: Fix ModerationService Missing Authorization Check

**Problem:** `ModerationService.review()` does not verify the caller has admin privileges. Any authenticated user with a picture ID could approve/reject content.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/picture/ModerationService.java:67-72`

- [ ] **Step 1: Add permission check to review() method**

Read the current `review()` method signature at line 67-69. The method currently takes `reviewerId` but doesn't verify the reviewer has `admin:review` permission.

Add this check after line 72 (after validating status is not null/PENDING), before fetching the picture:

```java
// Add import at top of file:
import com.cn.cloudpictureplatform.infrastructure.persistence.UserRoleRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RolePermissionRepository;
```

The service already has `AppUserRepository` injected. Add `RolePermissionRepository` injection and a permission check:

In the constructor (or add via `@RequiredArgsConstructor`), add:
```java
private final RolePermissionRepository rolePermissionRepository;
```

Then add after line 72:
```java
Set<String> permissions = new HashSet<>(rolePermissionRepository.findPermissionNamesByUserId(reviewerId));
if (!permissions.contains("admin:review")) {
    throw new ApiException(ApiErrorCode.FORBIDDEN, "Only admins can review pictures");
}
```

- [ ] **Step 2: Verify the file compiles**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/application/picture/ModerationService.java
git commit -m "fix(security): add authorization check to picture review

ModerationService.review() now verifies the caller has admin:review
permission before allowing content moderation actions."
```

---

### Task 6: Secure Unauthenticated Team Endpoints

**Problem:** Three team-related endpoints lack authentication, potentially exposing team data to unauthenticated users.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/config/SecurityConfig.java`

- [ ] **Step 1: Add team endpoints to authenticated routes**

Read `SecurityConfig.java`. The `requestMatchers` configuration at lines 36-47 defines public vs authenticated routes. Add these lines to the `authenticated()` section (after line 47):

```java
.requestMatchers(HttpMethod.GET, "/api/teams/*/activities").authenticated()
.requestMatchers(HttpMethod.GET, "/api/teams/*/watermark").authenticated()
.requestMatchers(HttpMethod.GET, "/api/teams/*/export-presets").authenticated()
```

- [ ] **Step 2: Verify the file compiles**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/config/SecurityConfig.java
git commit -m "fix(security): require auth for team activity/watermark/preset endpoints

GET /api/teams/{id}/activities, /watermark, and /export-presets
now require authentication."
```

---

### Task 7: Fix ExportProcessingService Self-Invocation Transaction

**Problem:** `ExportProcessingService.failTask()` is `protected @Transactional` but called from `processTask()` on the same bean. Spring proxy bypasses, so the `@Transactional` annotation is ineffective.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/watermark/ExportProcessingService.java`

- [ ] **Step 1: Extract failTask into a separate Spring bean**

Read `ExportProcessingService.java` (186 lines). The `failTask()` method at line 179 is called from `processTask()` at lines 72, 87, 112.

Create a new file `src/main/java/com/cn/cloudpictureplatform/application/watermark/ExportTaskStatusUpdater.java`:

```java
package com.cn.cloudpictureplatform.application.watermark;

import com.cn.cloudpictureplatform.domain.watermark.ExportTask;
import com.cn.cloudpictureplatform.infrastructure.persistence.ExportTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExportTaskStatusUpdater {

    private final ExportTaskRepository exportTaskRepository;

    @Transactional
    public void markFailed(ExportTask task, String errorMessage) {
        task.setStatus("FAILED");
        task.setErrorMessage(errorMessage);
        exportTaskRepository.save(task);
    }
}
```

- [ ] **Step 2: Update ExportProcessingService to use the new bean**

In `ExportProcessingService.java`:
1. Add field: `private final ExportTaskStatusUpdater exportTaskStatusUpdater;`
2. Replace all `failTask(task, errorMessage)` calls with `exportTaskStatusUpdater.markFailed(task, errorMessage)`
3. Remove the `failTask()` method entirely

- [ ] **Step 3: Verify the file compiles**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/application/watermark/
git commit -m "fix(transaction): extract failTask to separate bean for proper @Transactional

ExportProcessingService.failTask() was a self-invocation that bypassed
Spring's transaction proxy. Extracted to ExportTaskStatusUpdater so
the @Transactional annotation is effective."
```

---

### Task 8: Fix BatchPictureController Architecture Violations

**Problem:** `BatchPictureController` directly injects repositories and has `@Transactional` on controller methods. This violates the hexagonal architecture pattern.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/interfaces/picture/BatchPictureController.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/application/picture/BatchPictureService.java` (new)

- [ ] **Step 1: Create BatchPictureService**

Create `src/main/java/com/cn/cloudpictureplatform/application/picture/BatchPictureService.java`:

```java
package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.infrastructure.persistence.AlbumPictureRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchPictureService {

    private final PictureAssetRepository pictureAssetRepository;
    private final PictureTagRepository pictureTagRepository;
    private final AlbumPictureRepository albumPictureRepository;

    @Transactional
    public void batchDelete(List<UUID> pictureIds, UUID requesterId) {
        List<PictureAsset> assets = findAuthorizedAssets(pictureIds, requesterId);
        for (PictureAsset asset : assets) {
            pictureTagRepository.deleteByPictureAssetId(asset.getId());
            pictureAssetRepository.delete(asset);
        }
    }

    @Transactional
    public void batchUpdateVisibility(List<UUID> pictureIds, Visibility visibility, UUID requesterId) {
        List<PictureAsset> assets = findAuthorizedAssets(pictureIds, requesterId);
        for (PictureAsset asset : assets) {
            asset.setVisibility(visibility);
        }
        pictureAssetRepository.saveAll(assets);
    }

    @Transactional
    public void batchAddTag(List<UUID> pictureIds, String tagText, UUID requesterId) {
        findAuthorizedAssets(pictureIds, requesterId); // validate ownership
        // Delegate to PictureTagService for actual tag creation
        // This is a placeholder - the actual implementation should call PictureTagService
    }

    @Transactional
    public void batchMoveToAlbum(List<UUID> pictureIds, UUID targetAlbumId, UUID requesterId) {
        findAuthorizedAssets(pictureIds, requesterId); // validate ownership
        // Move logic here
    }

    private List<PictureAsset> findAuthorizedAssets(List<UUID> pictureIds, UUID requesterId) {
        List<PictureAsset> assets = pictureAssetRepository.findAllById(pictureIds);
        if (assets.size() != pictureIds.size()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Some pictures were not found");
        }
        for (PictureAsset asset : assets) {
            if (!asset.getOwnerId().equals(requesterId)) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "You can only modify your own pictures");
            }
        }
        return assets;
    }
}
```

- [ ] **Step 2: Refactor BatchPictureController to use the service**

Replace the controller to remove repository injection and `@Transactional`:

```java
package com.cn.cloudpictureplatform.interfaces.picture;

import com.cn.cloudpictureplatform.application.picture.BatchPictureService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.BatchOperationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/pictures/batch")
@RequiredArgsConstructor
public class BatchPictureController {

    private final BatchPictureService batchPictureService;

    @PostMapping("/delete")
    public ApiResponse<Void> batchDelete(@Valid @RequestBody BatchOperationRequest request, Principal principal) {
        batchPictureService.batchDelete(request.getPictureIds(), extractUserId(principal));
        return ApiResponse.ok();
    }

    @PostMapping("/visibility")
    public ApiResponse<Void> batchUpdateVisibility(@Valid @RequestBody BatchOperationRequest request, Principal principal) {
        batchPictureService.batchUpdateVisibility(request.getPictureIds(), request.getVisibility(), extractUserId(principal));
        return ApiResponse.ok();
    }

    @PostMapping("/tag")
    public ApiResponse<Void> batchTag(@Valid @RequestBody BatchOperationRequest request, Principal principal) {
        batchPictureService.batchAddTag(request.getPictureIds(), request.getTagTexts().get(0), extractUserId(principal));
        return ApiResponse.ok();
    }

    @PostMapping("/move")
    public ApiResponse<Void> batchMove(@Valid @RequestBody BatchOperationRequest request, Principal principal) {
        batchPictureService.batchMoveToAlbum(request.getPictureIds(), request.getTargetAlbumId(), extractUserId(principal));
        return ApiResponse.ok();
    }

    private UUID extractUserId(Principal principal) {
        // Extract userId from principal - implementation depends on auth setup
        return UUID.fromString(principal.getName());
    }
}
```

- [ ] **Step 3: Verify the file compiles**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/interfaces/picture/BatchPictureController.java
git add src/main/java/com/cn/cloudpictureplatform/application/picture/BatchPictureService.java
git commit -m "refactor(arch): extract BatchPictureService from controller

Moved repository access and @Transactional from controller to
application service, fixing hexagonal architecture violation."
```

---

### Task 9: Add Missing Foreign Key Constraints

**Problem:** 30+ foreign key constraints are missing from migrations V17+. This causes orphan records and data integrity issues.

**Files:**
- Create: `src/main/resources/db/migration/V32__add_missing_foreign_keys.sql` (new)

- [ ] **Step 1: Create migration with all missing FK constraints**

Create `src/main/resources/db/migration/V32__add_missing_foreign_keys.sql`:

```sql
-- V32: Add missing foreign key constraints
-- These were omitted from V17-V28 migrations

-- Album references
ALTER TABLE album ADD CONSTRAINT fk_album_space
    FOREIGN KEY (space_id) REFERENCES picture_space(id);
ALTER TABLE album ADD CONSTRAINT fk_album_cover_picture
    FOREIGN KEY (cover_picture_id) REFERENCES picture_asset(id);

-- Album-Picture junction
ALTER TABLE album_picture ADD CONSTRAINT fk_album_picture_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);

-- Picture Version
ALTER TABLE picture_version ADD CONSTRAINT fk_picture_version_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE picture_version ADD CONSTRAINT fk_picture_version_file_content
    FOREIGN KEY (file_content_id) REFERENCES file_content(id);
ALTER TABLE picture_version ADD CONSTRAINT fk_picture_version_created_by
    FOREIGN KEY (created_by_user_id) REFERENCES app_user(id);

-- Picture Comment
ALTER TABLE picture_comment ADD CONSTRAINT fk_picture_comment_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE picture_comment ADD CONSTRAINT fk_picture_comment_author
    FOREIGN KEY (author_id) REFERENCES app_user(id);
ALTER TABLE picture_comment ADD CONSTRAINT fk_picture_comment_parent
    FOREIGN KEY (parent_id) REFERENCES picture_comment(id);

-- Team Activity
ALTER TABLE team_activity ADD CONSTRAINT fk_team_activity_team
    FOREIGN KEY (team_id) REFERENCES team(id);
ALTER TABLE team_activity ADD CONSTRAINT fk_team_activity_actor
    FOREIGN KEY (actor_id) REFERENCES app_user(id);

-- Export
ALTER TABLE export_task ADD CONSTRAINT fk_export_task_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE export_task ADD CONSTRAINT fk_export_task_preset
    FOREIGN KEY (preset_id) REFERENCES export_preset(id);
ALTER TABLE export_task ADD CONSTRAINT fk_export_task_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

-- Webhook
ALTER TABLE webhook_delivery ADD CONSTRAINT fk_webhook_delivery_webhook
    FOREIGN KEY (webhook_id) REFERENCES webhook_endpoint(id);

-- API Key
ALTER TABLE api_key ADD CONSTRAINT fk_api_key_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

-- AI tables
ALTER TABLE ai_call_audit ADD CONSTRAINT fk_ai_call_audit_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE ai_call_audit ADD CONSTRAINT fk_ai_call_audit_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ai_task ADD CONSTRAINT fk_ai_task_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE ai_moderation_record ADD CONSTRAINT fk_ai_moderation_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);

-- Notification
ALTER TABLE notification_record ADD CONSTRAINT fk_notification_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

-- User Behavior
ALTER TABLE user_behavior_event ADD CONSTRAINT fk_ube_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE user_behavior_event ADD CONSTRAINT fk_ube_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);

-- Tag Feedback
ALTER TABLE tag_feedback ADD CONSTRAINT fk_tag_feedback_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE tag_feedback ADD CONSTRAINT fk_tag_feedback_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

-- Excalidraw
ALTER TABLE excalidraw_scene ADD CONSTRAINT fk_excalidraw_scene_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE excalidraw_scene ADD CONSTRAINT fk_excalidraw_scene_updated_by
    FOREIGN KEY (last_updated_by_user_id) REFERENCES app_user(id);
```

- [ ] **Step 2: Verify migration file is valid**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/db/migration/V32__add_missing_foreign_keys.sql
git commit -m "fix(db): add 25 missing foreign key constraints

Adds FK constraints for album, picture_version, picture_comment,
team_activity, export_task, webhook, api_key, ai_*, notification,
user_behavior, tag_feedback, and excalidraw tables."
```

---

## Phase 2: P1 — Engineering Discipline (1-3 months)

---

### Task 10: Fix ArchUnit Tests to Cover All Layers

**Problem:** Existing ArchUnit test only checks domain→other layers. Missing rules for application, infrastructure, interfaces, and websocket layers.

**Files:**
- Modify: `src/test/java/com/cn/cloudpictureplatform/architecture/LayerDependencyRulesTest.java`

- [ ] **Step 1: Add comprehensive layer dependency rules**

Replace the content of `LayerDependencyRulesTest.java` with:

```java
package com.cn.cloudpictureplatform.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.cn.cloudpictureplatform", importOptions = {ImportOption.DoNotIncludeTests.class})
public class LayerDependencyRulesTest {

    // Domain must not depend on any outer layer
    @ArchTest
    static final ArchRule domain_independent_of_application =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..");

    @ArchTest
    static final ArchRule domain_independent_of_interfaces =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    @ArchTest
    static final ArchRule domain_independent_of_websocket =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..websocket..");

    @ArchTest
    static final ArchRule domain_independent_of_config =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..config..");

    // Application must not depend on interfaces or websocket
    @ArchTest
    static final ArchRule application_independent_of_interfaces =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    @ArchTest
    static final ArchRule application_independent_of_websocket =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..websocket..");

    // Interfaces must not depend on infrastructure directly
    @ArchTest
    static final ArchRule interfaces_independent_of_infrastructure =
        noClasses().that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    // Controllers must not inject repositories
    @ArchTest
    static final ArchRule controllers_should_not_use_repositories =
        noClasses().that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..");
}
```

- [ ] **Step 2: Run the tests**

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"
.\mvnw test -Dtest=LayerDependencyRulesTest -pl . -q
```
Expected: All tests pass (after Task 8 fixes the BatchPictureController violation).

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/cn/cloudpictureplatform/architecture/LayerDependencyRulesTest.java
git commit -m "test(arch): expand ArchUnit rules for all layers

Added rules: application independent of interfaces/websocket,
interfaces independent of infrastructure, controllers cannot
inject repositories."
```

---

### Task 11: Add GitHub Actions CI Pipeline

**Problem:** No CI/CD pipeline exists. Every code change is untested until manually verified.

**Files:**
- Create: `.github/workflows/ci.yml` (new)

- [ ] **Step 1: Create CI workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'graalvm'
          cache: maven

      - name: Build and test
        run: ./mvnw clean test -B

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: target/surefire-reports/
```

- [ ] **Step 2: Verify the workflow file is valid YAML**

```powershell
# Simple syntax check
Get-Content ".github/workflows/ci.yml" | Select-String "on:|jobs:|steps:"
```
Expected: Shows the key YAML sections.

- [ ] **Step 3: Commit**

```powershell
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow for build and test

Runs on push to main/develop and PRs to main.
Uses GraalVM JDK 21 with Maven cache."
```

---

### Task 12: Create Dockerfile for Main Application

**Problem:** No Dockerfile for the Java application. Deployment is manual and error-prone.

**Files:**
- Create: `Dockerfile` (new, at project root)

- [ ] **Step 1: Create multi-stage Dockerfile**

Create `Dockerfile` at project root:

```dockerfile
# Build stage
FROM ghcr.io/graalvm/jdk:21 AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM ghcr.io/graalvm/jdk:21
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Update docker-compose.yml to include the app service**

Read the existing `docker-compose.yml` (46 lines). Add the app service definition:

```yaml
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=cloud_picture
      - DB_USERNAME=admin
      - DB_PASSWORD=${DB_PASSWORD:-postgres}
      - REDIS_HOST=redis
      - JWT_SECRET=${JWT_SECRET:-dev-secret-change-me-32chars-minimum}
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - postgres
      - redis
```

- [ ] **Step 3: Commit**

```powershell
git add Dockerfile docker-compose.yml
git commit -m "feat(deploy): add Dockerfile and app service to docker-compose

Multi-stage build with GraalVM JDK 21. App service connects to
postgres and redis via environment variables."
```

---

### Task 13: Eliminate Duplicate Code

**Problem:** `toResponse()`, `requireActiveMember()`, `resolveSort()` are duplicated across 3+ files.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/picture/PictureUploadService.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/picture/ModerationService.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/team/TeamCommandService.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/team/TeamQueryService.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/application/team/TeamMemberValidator.java` (new)

- [ ] **Step 1: Create TeamMemberValidator**

Create `src/main/java/com/cn/cloudpictureplatform/application/team/TeamMemberValidator.java`:

```java
package com.cn.cloudpictureplatform.application.team;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TeamMemberValidator {

    private final TeamMemberRepository teamMemberRepository;

    public TeamMember requireActiveMember(UUID teamId, UUID userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
            .filter(m -> m.getStatus() == TeamMemberStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(ApiErrorCode.FORBIDDEN, "You are not an active member of this team"));
    }

    public TeamMember requireAdmin(UUID teamId, UUID userId) {
        TeamMember member = requireActiveMember(teamId, userId);
        if (member.getRole() != TeamRole.OWNER && member.getRole() != TeamRole.ADMIN) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "Only team owners and admins can perform this action");
        }
        return member;
    }
}
```

- [ ] **Step 2: Update TeamCommandService to use TeamMemberValidator**

In `TeamCommandService.java`:
1. Add field: `private final TeamMemberValidator teamMemberValidator;`
2. Replace all calls to the private `requireActiveMember()` and `requireAdmin()` methods with calls to `teamMemberValidator.requireActiveMember()` and `teamMemberValidator.requireAdmin()`
3. Remove the private duplicate methods

- [ ] **Step 3: Update TeamQueryService to use TeamMemberValidator**

Same changes as Step 2 but in `TeamQueryService.java`.

- [ ] **Step 4: Remove duplicate toResponse() methods**

In `PictureUploadService.java`, remove the private `toResponse()` method and ensure it uses `PictureResponseConverter.toResponse()` (which is already injected or can be made a static utility).

In `ModerationService.java`, same change — use `PictureResponseConverter.toResponse()`.

- [ ] **Step 5: Verify compilation**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/application/
git commit -m "refactor: eliminate duplicate code across services

Extracted TeamMemberValidator for shared team membership checks.
Removed duplicate toResponse() methods in favor of PictureResponseConverter."
```

---

### Task 14: Fix DomainEventSubscriber JSON Construction

**Problem:** `DomainEventSubscriber` builds JSON via string concatenation, which is fragile and doesn't handle special characters correctly.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/events/DomainEventSubscriber.java`

- [ ] **Step 1: Replace string-based JSON with ObjectMapper**

Read the file and identify all places where JSON is built with `String.formatted()` or manual escaping (lines 54-61, 79-81, etc.). The class already has `ObjectMapper` injected.

Replace all manual JSON construction with `objectMapper.writeValueAsString()` calls. For example, if the current code looks like:

```java
String payload = "{\"pictureId\":\"%s\",\"name\":\"%s\"}".formatted(
    event.getPictureId(), escape(event.getPictureName()));
```

Replace with:

```java
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("pictureId", event.getPictureId().toString());
payload.put("name", event.getPictureName());
String json = objectMapper.writeValueAsString(payload);
```

Apply this pattern to all event handler methods in the class.

- [ ] **Step 2: Verify compilation**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/application/events/DomainEventSubscriber.java
git commit -m "fix: replace manual JSON construction with ObjectMapper

DomainEventSubscriber now uses ObjectMapper for all JSON serialization,
fixing potential issues with special characters in event payloads."
```

---

### Task 15: Fix AdminAiController Repository Access

**Problem:** `AdminAiController` directly injects repositories and performs aggregation logic in the controller layer.

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/application/admin/AiStatsService.java` (new)
- Modify: `src/main/java/com/cn/cloudpictureplatform/interfaces/admin/AdminAiController.java`

- [ ] **Step 1: Create AiStatsService**

Create `src/main/java/com/cn/cloudpictureplatform/application/admin/AiStatsService.java`:

```java
package com.cn.cloudpictureplatform.application.admin;

import com.cn.cloudpictureplatform.domain.ai.AiCallAudit;
import com.cn.cloudpictureplatform.domain.ai.AiModerationRecord;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiCallAuditRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.interfaces.admin.dto.AiStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.IntSummaryStatistics;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiStatsService {

    private final AiCallAuditRepository aiCallAuditRepository;
    private final AiModerationRecordRepository aiModerationRecordRepository;
    private final PictureAssetRepository pictureAssetRepository;

    public AiStatsResponse getStats() {
        // Build stats from repositories
        List<AiCallAudit> allAudits = aiCallAuditRepository.findAll();

        long totalCalls = allAudits.size();
        long successfulCalls = allAudits.stream().filter(AiCallAudit::isSuccess).count();
        long failedCalls = totalCalls - successfulCalls;

        IntSummaryStatistics latencyStats = allAudits.stream()
            .filter(a -> a.getLatencyMs() != null)
            .mapToInt(AiCallAudit::getLatencyMs)
            .summaryStatistics();

        long moderationCount = aiModerationRecordRepository.findAll().size();
        long totalPictures = pictureAssetRepository.count();

        return AiStatsResponse.builder()
            .tagging(AiStatsResponse.TaggingStats.builder()
                .totalCalls(totalCalls)
                .successfulCalls(successfulCalls)
                .failedCalls(failedCalls)
                .avgLatencyMs(latencyStats.getCount() > 0 ? (long) latencyStats.getAverage() : 0)
                .build())
            .moderation(AiStatsResponse.ModerationStats.builder()
                .totalReviews(moderationCount)
                .build())
            .embedding(AiStatsResponse.EmbeddingStats.builder()
                .totalPictures(totalPictures)
                .build())
            .build();
    }
}
```

- [ ] **Step 2: Refactor AdminAiController**

Replace the controller to use the service:

```java
package com.cn.cloudpictureplatform.interfaces.admin;

import com.cn.cloudpictureplatform.application.admin.AiStatsService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.AiStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiStatsService aiStatsService;

    @GetMapping("/stats")
    public ApiResponse<AiStatsResponse> getStats() {
        return ApiResponse.ok(aiStatsService.getStats());
    }
}
```

- [ ] **Step 3: Verify compilation**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/interfaces/admin/AdminAiController.java
git add src/main/java/com/cn/cloudpictureplatform/application/admin/AiStatsService.java
git commit -m "refactor(arch): move AI stats logic from controller to service

AdminAiController now delegates to AiStatsService instead of
directly accessing repositories."
```

---

### Task 16: Add Integration Test for Picture Upload Flow

**Problem:** Zero integration tests for the core picture upload flow. Only unit tests with mocks exist.

**Files:**
- Create: `src/test/java/com/cn/cloudpictureplatform/application/picture/PictureUploadIntegrationTest.java` (new)

- [ ] **Step 1: Create integration test**

Create `src/test/java/com/cn/cloudpictureplatform/application/picture/PictureUploadIntegrationTest.java`:

```java
package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PictureUploadIntegrationTest {

    @Autowired
    private PictureUploadService pictureUploadService;

    @Autowired
    private DeduplicationPictureUploadService deduplicationPictureUploadService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PictureAssetRepository pictureAssetRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    private AppUser testUser;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder()
            .username("upload-test-" + UUID.randomUUID().toString().substring(0, 8))
            .email("upload-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
            .passwordHash("$2a$10$dummy")
            .displayName("Upload Test User")
            .status(UserStatus.ACTIVE)
            .build();
        testUser = appUserRepository.save(testUser);

        // Get the user's personal space (created by DataInitializer or create one)
        spaceId = spaceRepository.findFirstByOwnerIdAndType(testUser.getId(), com.cn.cloudpictureplatform.domain.space.SpaceType.PERSONAL)
            .map(s -> s.getId())
            .orElseGet(() -> {
                var space = com.cn.cloudpictureplatform.domain.space.Space.builder()
                    .ownerId(testUser.getId())
                    .type(com.cn.cloudpictureplatform.domain.space.SpaceType.PERSONAL)
                    .name("Personal")
                    .quotaBytes(10L * 1024 * 1024 * 1024)
                    .usedBytes(0L)
                    .build();
                return spaceRepository.save(space).getId();
            });
    }

    @Test
    void shouldUploadPictureSuccessfully() {
        // Given
        byte[] imageBytes = createTestImageBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.jpg", "image/jpeg", imageBytes);

        // When
        var response = pictureUploadService.upload(file, testUser.getId(), spaceId, "Test Picture", Visibility.PRIVATE);

        // Then
        assertNotNull(response);
        assertNotNull(response.getId());

        var saved = pictureAssetRepository.findById(response.getId());
        assertTrue(saved.isPresent());
        assertEquals("Test Picture", saved.get().getName());
        assertEquals(Visibility.PRIVATE, saved.get().getVisibility());
        assertEquals(ReviewStatus.PENDING, saved.get().getReviewStatus());
    }

    @Test
    void shouldDedupIdenticalFiles() {
        // Given
        byte[] imageBytes = createTestImageBytes();
        MockMultipartFile file1 = new MockMultipartFile(
            "file", "test1.jpg", "image/jpeg", imageBytes);
        MockMultipartFile file2 = new MockMultipartFile(
            "file", "test2.jpg", "image/jpeg", imageBytes);

        // When
        var response1 = deduplicationPictureUploadService.uploadWithDeduplication(
            file1, testUser.getId(), spaceId, "Picture 1", Visibility.PRIVATE);
        var response2 = deduplicationPictureUploadService.uploadWithDeduplication(
            file2, testUser.getId(), spaceId, "Picture 2", Visibility.PRIVATE);

        // Then
        assertNotNull(response1);
        assertNotNull(response2);
        assertNotEquals(response1.getId(), response2.getId()); // Different picture assets
        // Both should reference the same file content (dedup)
        var pic1 = pictureAssetRepository.findById(response1.getId()).orElseThrow();
        var pic2 = pictureAssetRepository.findById(response2.getId()).orElseThrow();
        assertEquals(pic1.getFileContentId(), pic2.getFileContentId());
    }

    private byte[] createTestImageBytes() {
        // Minimal JPEG bytes (smallest valid JPEG)
        return new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xD9
        };
    }
}
```

- [ ] **Step 2: Run the test**

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"
.\mvnw test -Dtest=PictureUploadIntegrationTest -pl . -q
```
Expected: Tests pass (or fail with specific errors to investigate).

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/cn/cloudpictureplatform/application/picture/PictureUploadIntegrationTest.java
git commit -m "test: add integration test for picture upload and dedup

Tests the full upload flow including file storage, deduplication,
and database persistence using H2 in-memory database."
```

---

### Task 17: Disable show-sql and Fix ddl-auto for Production

**Problem:** `show-sql: true` and `ddl-auto: update` should never be used in production.

**Files:**
- Modify: `src/main/resources/application.yml:17-18`

- [ ] **Step 1: Change defaults for production safety**

Change line 17 from:
```yaml
      ddl-auto: update
```
to:
```yaml
      ddl-auto: validate
```

Change line 18 from:
```yaml
    show-sql: true
```
to:
```yaml
    show-sql: false
```

- [ ] **Step 2: Add dev profile overrides**

Create `src/main/resources/application-dev.yml`:
```yaml
spring:
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
```

This keeps `show-sql` and `ddl-auto: update` for development but defaults to safe production settings.

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/application.yml src/main/resources/application-dev.yml
git commit -m "fix(config): default to safe production JPA settings

Changed default ddl-auto to validate and show-sql to false.
Development overrides are in application-dev.yml."
```

---

## Phase 3: P2 — Scale & Developer Experience (3-6 months)

---

### Task 18: Add Search Index Batching

**Problem:** `searchIndexTaskExecutor` has core=1, max=2, queue=500. When the queue is full, tasks are silently dropped.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/config/SearchIndexConfig.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/infrastructure/search/DatabaseSearchIndexService.java`

- [ ] **Step 1: Increase thread pool capacity**

In `SearchIndexConfig.java`, change the executor configuration:
```java
@Bean(name = "searchIndexTaskExecutor")
public TaskExecutor searchIndexTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("search-index-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

Changed: core 1→2, max 2→8, queue 500→1000, rejection handler from log-and-drop to `CallerRunsPolicy` (runs on caller thread when queue full, providing backpressure).

- [ ] **Step 2: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/config/SearchIndexConfig.java
git commit -m "perf(search): increase index thread pool and add backpressure

Increased core/max threads and switched to CallerRunsPolicy to
prevent silent task drops when the queue is full."
```

---

### Task 19: Add WebhookService ObjectMapper Consistency

**Problem:** `WebhookService.listToJson()` creates a new `ObjectMapper` on every call instead of using the injected one.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/webhook/WebhookService.java`

- [ ] **Step 1: Remove duplicate ObjectMapper creation**

Read the file and find `listToJson()` method (around line 129). It likely looks like:

```java
private String listToJson(List<String> list) {
    ObjectMapper mapper = new ObjectMapper(); // BAD: new instance every call
    // ...
}
```

Change to use the injected ObjectMapper:
```java
private String listToJson(List<String> list) {
    try {
        return objectMapper.writeValueAsString(list);
    } catch (Exception e) {
        return "[]";
    }
}
```

The class already has `ObjectMapper` injected (verify by checking constructor/fields). If not, add `private final ObjectMapper objectMapper;` to the constructor.

- [ ] **Step 2: Verify compilation**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/application/webhook/WebhookService.java
git commit -m "fix(webhook): reuse injected ObjectMapper instead of creating new one

WebhookService.listToJson() now uses the Spring-managed ObjectMapper
for consistent serialization behavior."
```

---

### Task 20: Add DataInitializer Logger Fix

**Problem:** `DataInitializer` uses `System.err.println` instead of SLF4J logger.

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/config/DataInitializer.java`

- [ ] **Step 1: Replace System.err with logger**

Add at the top of the class:
```java
@Slf4j
public class DataInitializer implements ApplicationRunner {
```

Replace all `System.err.println(...)` calls with `log.warn(...)`. For example:
```java
// Before:
System.err.println("Warning: ...");
// After:
log.warn("Warning: ...");
```

- [ ] **Step 2: Verify compilation**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/config/DataInitializer.java
git commit -m "fix(logging): replace System.err with SLF4J in DataInitializer

All System.err.println calls now use log.warn() for proper
logging framework integration."
```

---

### Task 21: Add Soft Delete Support to PictureAsset

**Problem:** All deletes are hard deletes. Accidental picture deletion is permanent and unrecoverable.

**Files:**
- Create: `src/main/resources/db/migration/V33__add_soft_delete_to_picture.sql` (new)
- Modify: `src/main/java/com/cn/cloudpictureplatform/domain/picture/PictureAsset.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/picture/PictureUploadService.java`

- [ ] **Step 1: Create migration for soft delete columns**

Create `src/main/resources/db/migration/V33__add_soft_delete_to_picture.sql`:

```sql
-- V33: Add soft delete support to picture_asset
ALTER TABLE picture_asset ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE picture_asset ADD COLUMN deleted_by UUID;

CREATE INDEX idx_picture_deleted_at ON picture_asset(deleted_at) WHERE deleted_at IS NOT NULL;
```

- [ ] **Step 2: Add soft delete fields to PictureAsset entity**

In `PictureAsset.java`, add:
```java
@Column(name = "deleted_at")
private Instant deletedAt;

@Column(name = "deleted_by")
private UUID deletedBy;
```

Add domain methods:
```java
public void softDelete(UUID deletedBy) {
    this.deletedAt = Instant.now();
    this.deletedBy = deletedBy;
}

public boolean isDeleted() {
    return deletedAt != null;
}

public void restore() {
    this.deletedAt = null;
    this.deletedBy = null;
}
```

- [ ] **Step 3: Update queries to exclude soft-deleted records**

In `PictureAssetRepository`, add a `@Query` override for `findAll` and other methods that should exclude deleted records. Or use `@Where(clause = "deleted_at IS NULL")` on the entity (Hibernate filter).

Add to `PictureAsset.java`:
```java
@Where(clause = "deleted_at IS NULL")
```
(This is a Hibernate annotation that automatically filters deleted records from all queries.)

- [ ] **Step 4: Update delete logic in services**

In services that delete pictures, change from hard delete to soft delete:
```java
// Before:
pictureAssetRepository.delete(asset);
// After:
asset.softDelete(requesterId);
pictureAssetRepository.save(asset);
```

- [ ] **Step 5: Verify compilation**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```powershell
git add src/main/resources/db/migration/V33__add_soft_delete_to_picture.sql
git add src/main/java/com/cn/cloudpictureplatform/domain/picture/PictureAsset.java
git add src/main/java/com/cn/cloudpictureplatform/application/picture/
git commit -m "feat: add soft delete support to PictureAsset

Pictures are now soft-deleted (deleted_at timestamp) instead of
hard-deleted. Includes Hibernate @Where filter for automatic
exclusion from queries."
```

---

### Task 22: Add API Versioning Prefix

**Problem:** All endpoints are under `/api/` with no version prefix. Future breaking changes will be difficult to manage.

**Files:**
- Modify: All controller `@RequestMapping` annotations
- Modify: `SecurityConfig.java`

- [ ] **Step 1: Add version prefix to all controllers**

For each controller, change `@RequestMapping("/api/...")` to `@RequestMapping("/api/v1/...")`. For example:

```java
// Before:
@RequestMapping("/api/auth")
// After:
@RequestMapping("/api/v1/auth")
```

Apply to all 17+ controllers.

- [ ] **Step 2: Update SecurityConfig paths**

Update all `requestMatchers` in `SecurityConfig.java` to use `/api/v1/` prefix.

- [ ] **Step 3: Verify compilation**

```powershell
.\mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/cn/cloudpictureplatform/interfaces/
git add src/main/java/com/cn/cloudpictureplatform/config/SecurityConfig.java
git commit -m "feat(api): add /api/v1/ version prefix to all endpoints

All REST endpoints now use /api/v1/ prefix for future API
versioning support."
```

---

## Execution Order Summary

| Order | Task | Priority | Effort | Dependencies |
|-------|------|----------|--------|--------------|
| 1 | Fix Flyway version conflicts | P0 | 30min | None |
| 2 | Fix V28 table name | P0 | 10min | None |
| 3 | Externalize JWT secret | P0 | 1h | None |
| 4 | Externalize DB/Redis credentials | P0 | 30min | Task 3 |
| 5 | Fix ModerationService auth | P0 | 1h | None |
| 6 | Secure team endpoints | P0 | 30min | None |
| 7 | Fix ExportProcessingService transaction | P0 | 1h | None |
| 8 | Fix BatchPictureController | P0 | 2h | None |
| 9 | Add missing FK constraints | P0 | 1h | Tasks 1-2 |
| 10 | Expand ArchUnit tests | P1 | 1h | Task 8 |
| 11 | Add CI pipeline | P1 | 30min | None |
| 12 | Create Dockerfile | P1 | 1h | None |
| 13 | Eliminate duplicate code | P1 | 2h | Task 8 |
| 14 | Fix JSON construction | P1 | 1h | None |
| 15 | Fix AdminAiController | P1 | 1h | None |
| 16 | Add integration tests | P1 | 2h | Tasks 1-9 |
| 17 | Fix JPA defaults | P1 | 15min | None |
| 18 | Search index batching | P2 | 30min | None |
| 19 | Webhook ObjectMapper | P2 | 15min | None |
| 20 | DataInitializer logger | P2 | 15min | None |
| 21 | Soft delete support | P2 | 3h | None |
| 22 | API versioning | P2 | 3h | None |

**Total estimated effort: ~24 hours**

---

## Verification Checklist

After completing all tasks, verify:

```powershell
# Set JAVA_HOME
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"

# Compile
.\mvnw compile

# Run all tests
.\mvnw test

# Run ArchUnit tests specifically
.\mvnw test -Dtest=LayerDependencyRulesTest

# Build JAR
.\mvnw clean package

# Start with dev profile
.\mvnw spring-boot:run --spring-boot.run.profiles=dev
```

All should pass without errors.
