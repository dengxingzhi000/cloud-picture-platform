# Domain Event Bus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a domain event bus to decouple cross-aggregate collaboration, replacing direct service-to-service calls and scattered ApplicationEventPublisher usage.

**Architecture:** Domain events as records in `domain/events/` implementing a `DomainEvent` marker interface. `DomainEventBus` port in domain layer, `SpringDomainEventBus` adapter in infrastructure wrapping Spring's `ApplicationEventPublisher`. Single `DomainEventSubscriber` handles all events via `@TransactionalEventListener(AFTER_COMMIT)`.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring ApplicationEventPublisher, JPA, Outbox pattern

**Spec:** `docs/superpowers/specs/2026-05-21-domain-event-bus-design.md`

---

## File Structure

```
domain/events/
  DomainEvent.java              — marker interface (eventId, occurredAt defaults)
  DomainEventBus.java           — port interface (publish, publishAll)
  PictureUploadedEvent.java     — migrated from application/picture/
  PictureReviewedEvent.java     — migrated from application/picture/
  TeamInviteEvent.java          — new
  TeamMemberJoinedEvent.java    — new

infrastructure/events/
  SpringDomainEventBus.java     — @Primary adapter

application/events/
  DomainEventSubscriber.java    — consolidates PictureEventHandler
```

---

### Task 1: Create DomainEvent interface

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/events/DomainEvent.java`

- [ ] **Step 1: Create the DomainEvent marker interface**

```java
package com.cn.cloudpictureplatform.domain.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();

    static UUID defaultEventId() {
        return UUID.randomUUID();
    }

    static Instant defaultOccurredAt() {
        return Instant.now();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/events/DomainEvent.java
git commit -m "feat: add DomainEvent marker interface in domain layer"
```

---

### Task 2: Create DomainEventBus port

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/events/DomainEventBus.java`

- [ ] **Step 1: Create the DomainEventBus port interface**

```java
package com.cn.cloudpictureplatform.domain.events;

import java.util.List;

public interface DomainEventBus {
    void publish(DomainEvent event);

    void publishAll(List<? extends DomainEvent> events);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/events/DomainEventBus.java
git commit -m "feat: add DomainEventBus port interface in domain layer"
```

---

### Task 3: Create SpringDomainEventBus adapter

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/infrastructure/events/SpringDomainEventBus.java`

- [ ] **Step 1: Create the adapter**

```java
package com.cn.cloudpictureplatform.infrastructure.events;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import com.cn.cloudpictureplatform.domain.events.DomainEvent;
import com.cn.cloudpictureplatform.domain.events.DomainEventBus;

@Component
@Primary
public class SpringDomainEventBus implements DomainEventBus {

    private final ApplicationEventPublisher publisher;

    public SpringDomainEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publishAll(List<? extends DomainEvent> events) {
        events.forEach(publisher::publishEvent);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/infrastructure/events/SpringDomainEventBus.java
git commit -m "feat: add SpringDomainEventBus adapter in infrastructure layer"
```

---

### Task 4: Migrate PictureUploadedEvent to domain/events/

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/events/PictureUploadedEvent.java`
- Delete: `src/main/java/com/cn/cloudpictureplatform/application/picture/PictureUploadedEvent.java`

- [ ] **Step 1: Create new PictureUploadedEvent implementing DomainEvent**

```java
package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record PictureUploadedEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID pictureId,
        String pictureName,
        UUID ownerId,
        UUID spaceId,
        UUID teamId,
        boolean isPublic
) implements DomainEvent {

    public PictureUploadedEvent(UUID pictureId, String pictureName, UUID ownerId,
                                UUID spaceId, UUID teamId, boolean isPublic) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                pictureId, pictureName, ownerId, spaceId, teamId, isPublic);
    }
}
```

- [ ] **Step 2: Delete old event file**

```bash
rm src/main/java/com/cn/cloudpictureplatform/application/picture/PictureUploadedEvent.java
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/events/PictureUploadedEvent.java
git add src/main/java/com/cn/cloudpictureplatform/application/picture/PictureUploadedEvent.java
git commit -m "refactor: migrate PictureUploadedEvent to domain/events/"
```

---

### Task 5: Migrate PictureReviewedEvent to domain/events/

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/events/PictureReviewedEvent.java`
- Delete: `src/main/java/com/cn/cloudpictureplatform/application/picture/PictureReviewedEvent.java`

- [ ] **Step 1: Create new PictureReviewedEvent implementing DomainEvent**

```java
package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record PictureReviewedEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID pictureId,
        String pictureName,
        UUID ownerId,
        boolean approved,
        String reason
) implements DomainEvent {

    public PictureReviewedEvent(UUID pictureId, String pictureName, UUID ownerId,
                                boolean approved, String reason) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                pictureId, pictureName, ownerId, approved, reason);
    }
}
```

- [ ] **Step 2: Delete old event file**

```bash
rm src/main/java/com/cn/cloudpictureplatform/application/picture/PictureReviewedEvent.java
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/events/PictureReviewedEvent.java
git add src/main/java/com/cn/cloudpictureplatform/application/picture/PictureReviewedEvent.java
git commit -m "refactor: migrate PictureReviewedEvent to domain/events/"
```

---

### Task 6: Create TeamInviteEvent

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/events/TeamInviteEvent.java`

- [ ] **Step 1: Create TeamInviteEvent**

```java
package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record TeamInviteEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID teamId,
        String teamName,
        String inviteeUsername,
        String inviterUsername
) implements DomainEvent {

    public TeamInviteEvent(UUID teamId, String teamName,
                           String inviteeUsername, String inviterUsername) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                teamId, teamName, inviteeUsername, inviterUsername);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/events/TeamInviteEvent.java
git commit -m "feat: add TeamInviteEvent domain event"
```

---

### Task 7: Create TeamMemberJoinedEvent

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/events/TeamMemberJoinedEvent.java`

- [ ] **Step 1: Create TeamMemberJoinedEvent**

```java
package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record TeamMemberJoinedEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID teamId,
        String teamName,
        UUID userId,
        String username
) implements DomainEvent {

    public TeamMemberJoinedEvent(UUID teamId, String teamName,
                                 UUID userId, String username) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                teamId, teamName, userId, username);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/events/TeamMemberJoinedEvent.java
git commit -m "feat: add TeamMemberJoinedEvent domain event"
```

---

### Task 8: Create DomainEventSubscriber

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/application/events/DomainEventSubscriber.java`

- [ ] **Step 1: Create DomainEventSubscriber with handlers for all events**

This consolidates the logic from `PictureEventHandler` (which will be deleted) and adds handlers for the new events.

```java
package com.cn.cloudpictureplatform.application.events;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.application.outbox.OutboxService;
import com.cn.cloudpictureplatform.domain.events.PictureReviewedEvent;
import com.cn.cloudpictureplatform.domain.events.PictureUploadedEvent;
import com.cn.cloudpictureplatform.domain.events.TeamInviteEvent;
import com.cn.cloudpictureplatform.domain.events.TeamMemberJoinedEvent;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DomainEventSubscriber {

    private final OutboxService outboxService;
    private final AppUserRepository appUserRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ObjectMapper objectMapper;

    public DomainEventSubscriber(
            OutboxService outboxService,
            AppUserRepository appUserRepository,
            TeamMemberRepository teamMemberRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxService = outboxService;
        this.appUserRepository = appUserRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureReviewed(PictureReviewedEvent event) {
        AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
        String payload = toJson("""
                {"ownerUsername":"%s","pictureName":"%s","approved":%s,"reason":"%s"}
                """.formatted(
                owner == null ? "" : owner.getUsername(),
                escape(event.pictureName()),
                event.approved(),
                escape(event.reason() != null ? event.reason() : "")
        ));
        if (payload != null) {
            outboxService.writeEvent("picture", event.pictureId(), "REVIEW_DECISION", payload);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureUploaded(PictureUploadedEvent event) {
        AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
        String ownerUsername = owner == null ? "" : owner.getUsername();

        String uploadPayload = toJson("""
                {"ownerUsername":"%s","pictureName":"%s"}
                """.formatted(ownerUsername, escape(event.pictureName())));
        if (uploadPayload != null) {
            outboxService.writeEvent("picture", event.pictureId(), "UPLOAD_COMPLETE", uploadPayload);
        }

        if (event.isPublic()) {
            String adminPayload = toJson("""
                    {"uploaderUsername":"%s","pictureName":"%s"}
                    """.formatted(ownerUsername, escape(event.pictureName())));
            if (adminPayload != null) {
                outboxService.writeEvent("picture", event.pictureId(), "ADMIN_NEW_UPLOAD", adminPayload);
            }
        }

        if (event.teamId() != null) {
            Collection<String> usernames = teamMemberRepository
                    .findByTeamIdAndStatus(event.teamId(), TeamMemberStatus.ACTIVE)
                    .stream()
                    .map(TeamMember::getUserId)
                    .filter(userId -> !userId.equals(event.ownerId()))
                    .map(userId -> appUserRepository.findById(userId).orElse(null))
                    .filter(Objects::nonNull)
                    .map(AppUser::getUsername)
                    .filter(StringUtils::hasText)
                    .toList();
            String teamPayload = toJson("""
                    {"usernames":[""" + usernames.stream().map(u -> "\"" + u + "\"").collect(Collectors.joining(",")) + """
                    ],"pictureName":"%s","uploaderUsername":"%s"}
                    """.formatted(escape(event.pictureName()), ownerUsername));
            if (teamPayload != null) {
                outboxService.writeEvent("picture", event.pictureId(), "TEAM_UPLOAD", teamPayload);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamInvite(TeamInviteEvent event) {
        String payload = toJson("""
                {"teamName":"%s","inviterUsername":"%s"}
                """.formatted(escape(event.teamName()), escape(event.inviterUsername())));
        if (payload != null) {
            outboxService.writeEvent("team", event.teamId(), "TEAM_INVITE", payload);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamMemberJoined(TeamMemberJoinedEvent event) {
        String payload = toJson("""
                {"teamName":"%s","username":"%s"}
                """.formatted(escape(event.teamName()), escape(event.username())));
        if (payload != null) {
            outboxService.writeEvent("team", event.teamId(), "TEAM_MEMBER_JOINED", payload);
        }
    }

    private String toJson(String raw) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(raw));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/events/DomainEventSubscriber.java
git commit -m "feat: add DomainEventSubscriber consolidating all event handlers"
```

---

### Task 9: Add TEAM_INVITE and TEAM_MEMBER_JOINED handling to OutboxProcessor

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/outbox/OutboxProcessor.java`

- [ ] **Step 1: Read the current OutboxProcessor**

Read `src/main/java/com/cn/cloudpictureplatform/application/outbox/OutboxProcessor.java` to understand the current structure.

- [ ] **Step 2: Add team event handling to processEvent()**

In the `processEvent()` method, add a case for `"team"` aggregate type:

```java
private void processEvent(OutboxEvent event) {
    switch (event.getAggregateType()) {
        case "picture" -> handlePictureEvent(event);
        case "team" -> handleTeamEvent(event);
        default -> log.warn("Unknown outbox aggregate type: {}", event.getAggregateType());
    }
}
```

Add the `handleTeamEvent()` method:

```java
private void handleTeamEvent(OutboxEvent event) {
    JsonNode payload = parsePayload(event.getPayload());
    if (payload == null) return;
    UUID teamId = event.getAggregateId();
    String teamName = payload.path("teamName").asText("unknown");

    switch (event.getEventType()) {
        case "TEAM_INVITE" -> {
            String inviterUsername = payload.path("inviterUsername").asText("unknown");
            notificationPublisher.notifyTeamInvite(
                    payload.path("inviteeUsername").asText("unknown"),
                    teamId, teamName, inviterUsername);
        }
        case "TEAM_MEMBER_JOINED" -> {
            String username = payload.path("username").asText("unknown");
            notificationPublisher.notifyTeamMemberJoined(username, teamId, teamName);
        }
        default -> log.warn("Unknown team event type: {}", event.getEventType());
    }
}
```

- [ ] **Step 3: Add TEAM_MEMBER_JOINED to NotificationKind enum**

In `src/main/java/com/cn/cloudpictureplatform/websocket/dto/NotificationMessage.java`, add to the enum:

```java
public enum NotificationKind {
    PICTURE_APPROVED,
    PICTURE_REJECTED,
    REVIEW_PENDING,
    TEAM_INVITE,
    UPLOAD_COMPLETE,
    TEAM_PICTURE_UPLOADED,
    TEAM_MEMBER_JOINED
}
```

- [ ] **Step 4: Add notifyTeamMemberJoined to NotificationPublisher**

In `src/main/java/com/cn/cloudpictureplatform/websocket/NotificationPublisher.java`, add:

```java
public void notifyTeamMemberJoined(String username, UUID teamId, String teamName) {
    NotificationMessage notification = NotificationMessage.builder()
            .kind(NotificationMessage.NotificationKind.TEAM_MEMBER_JOINED)
            .title("New team member")
            .body(username + " joined team \"" + teamName + "\".")
            .targetId(teamId)
            .timestamp(Instant.now())
            .build();
    sendToUser(username, notification);
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/outbox/OutboxProcessor.java
git add src/main/java/com/cn/cloudpictureplatform/websocket/NotificationPublisher.java
git add src/main/java/com/cn/cloudpictureplatform/websocket/dto/NotificationMessage.java
git commit -m "feat: add team event handling to OutboxProcessor and NotificationPublisher"
```

---

### Task 10: Migrate ModerationService to use DomainEventBus

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/picture/ModerationService.java`

- [ ] **Step 1: Replace ApplicationEventPublisher with DomainEventBus**

Change the import and field:
- Remove: `private final org.springframework.context.ApplicationEventPublisher eventPublisher;`
- Add: `private final com.cn.cloudpictureplatform.domain.events.DomainEventBus domainEventBus;`

Update constructor parameter:
- Replace: `org.springframework.context.ApplicationEventPublisher eventPublisher`
- With: `com.cn.cloudpictureplatform.domain.events.DomainEventBus domainEventBus`

Update all `eventPublisher.publishEvent(...)` calls to `domainEventBus.publish(...)`.

Update the event import:
- Remove: `com.cn.cloudpictureplatform.application.picture.PictureReviewedEvent`
- Add: `com.cn.cloudpictureplatform.domain.events.PictureReviewedEvent`

- [ ] **Step 2: Compile to verify**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/picture/ModerationService.java
git commit -m "refactor: ModerationService uses DomainEventBus instead of ApplicationEventPublisher"
```

---

### Task 11: Migrate PictureUploadService to use DomainEventBus

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/picture/PictureUploadService.java`

- [ ] **Step 1: Replace ApplicationEventPublisher with DomainEventBus**

Change the import and field:
- Remove: `private final org.springframework.context.ApplicationEventPublisher eventPublisher;`
- Add: `private final com.cn.cloudpictureplatform.domain.events.DomainEventBus domainEventBus;`

Update constructor parameter:
- Replace: `org.springframework.context.ApplicationEventPublisher eventPublisher`
- With: `com.cn.cloudpictureplatform.domain.events.DomainEventBus domainEventBus`

Update `eventPublisher.publishEvent(...)` call to `domainEventBus.publish(...)`.

Update the event import:
- Remove: `com.cn.cloudpictureplatform.application.picture.PictureUploadedEvent`
- Add: `com.cn.cloudpictureplatform.domain.events.PictureUploadedEvent`

- [ ] **Step 2: Compile to verify**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/picture/PictureUploadService.java
git commit -m "refactor: PictureUploadService uses DomainEventBus instead of ApplicationEventPublisher"
```

---

### Task 12: Migrate DeduplicationPictureUploadService to use DomainEventBus

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/picture/DeduplicationPictureUploadService.java`

- [ ] **Step 1: Read the current file to understand dependencies**

Read the full `DeduplicationPictureUploadService.java` file.

- [ ] **Step 2: Replace notificationPublisher with DomainEventBus**

Remove direct `notificationPublisher` calls in `notifyUploadRelatedParties()` and replace with event publishing.

Change fields:
- Remove: `private final NotificationPublisher notificationPublisher;`
- Remove: `private final AppUserRepository appUserRepository;` (if only used for notifications)
- Remove: `private final TeamMemberRepository teamMemberRepository;` (if only used for notifications)
- Add: `private final com.cn.cloudpictureplatform.domain.events.DomainEventBus domainEventBus;`

Replace the body of `notifyUploadRelatedParties()` with:
```java
private void notifyUploadRelatedParties(PictureAsset asset, Space space, UUID ownerId) {
    domainEventBus.publish(new PictureUploadedEvent(
            asset.getId(), asset.getName(), ownerId,
            space.getId(), space.getTeamId(),
            asset.getVisibility() == Visibility.PUBLIC
    ));
}
```

Update the event import:
- Add: `com.cn.cloudpictureplatform.domain.events.PictureUploadedEvent`

- [ ] **Step 3: Compile to verify**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/picture/DeduplicationPictureUploadService.java
git commit -m "refactor: DeduplicationPictureUploadService uses DomainEventBus for notifications"
```

---

### Task 13: Migrate TeamCommandService to use DomainEventBus

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/team/TeamCommandService.java`

- [ ] **Step 1: Read the current file to understand dependencies**

Read the full `TeamCommandService.java` file.

- [ ] **Step 2: Replace notificationPublisher with DomainEventBus**

Change fields:
- Remove: `private final NotificationPublisher notificationPublisher;`
- Add: `private final com.cn.cloudpictureplatform.domain.events.DomainEventBus domainEventBus;`

In the `invite()` method, replace the direct notification call:
```java
// Before:
notificationPublisher.notifyTeamInvite(
        invitee.getUsername(), teamId, team.getName(),
        inviterUser == null ? "unknown" : inviterUser.getUsername());

// After:
domainEventBus.publish(new TeamInviteEvent(
        teamId, team.getName(),
        invitee.getUsername(),
        inviterUser == null ? "unknown" : inviterUser.getUsername()));
```

In the `acceptInvite()` method, add event publishing:
```java
domainEventBus.publish(new TeamMemberJoinedEvent(
        teamId, team.getName(),
        user.getId(), user.getUsername()));
```

Update imports:
- Add: `com.cn.cloudpictureplatform.domain.events.TeamInviteEvent`
- Add: `com.cn.cloudpictureplatform.domain.events.TeamMemberJoinedEvent`

- [ ] **Step 3: Compile to verify**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/team/TeamCommandService.java
git commit -m "refactor: TeamCommandService uses DomainEventBus for notifications"
```

---

### Task 14: Delete old PictureEventHandler

**Files:**
- Delete: `src/main/java/com/cn/cloudpictureplatform/application/picture/PictureEventHandler.java`

- [ ] **Step 1: Verify no remaining references to PictureEventHandler**

Run: `grep -r "PictureEventHandler" --include="*.java" src/`

Expected: No results (all references should have been removed in previous tasks).

- [ ] **Step 2: Delete the file**

```bash
rm src/main/java/com/cn/cloudpictureplatform/application/picture/PictureEventHandler.java
```

- [ ] **Step 3: Compile to verify**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/picture/PictureEventHandler.java
git commit -m "refactor: delete PictureEventHandler replaced by DomainEventSubscriber"
```

---

### Task 15: Final compile and verify

- [ ] **Step 1: Full compile**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

Expected: BUILD SUCCESS

- [ ] **Step 2: Run tests (if available)**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw test`

Expected: All tests pass (or known failures only).

- [ ] **Step 3: Verify no remaining references to old event locations**

Run: `grep -r "application.picture.PictureUploadedEvent\|application.picture.PictureReviewedEvent" --include="*.java" src/`

Expected: No results.

- [ ] **Step 4: Final commit with verification**

```bash
git add -A
git commit -m "feat: complete domain event bus implementation

- DomainEvent interface in domain/events/
- DomainEventBus port in domain/events/
- SpringDomainEventBus adapter in infrastructure/events/
- DomainEventSubscriber consolidates all event handling
- Migrated PictureUploadedEvent, PictureReviewedEvent to domain layer
- Added TeamInviteEvent, TeamMemberJoinedEvent
- Eliminated direct notification calls from services
- Deleted old PictureEventHandler"
```
