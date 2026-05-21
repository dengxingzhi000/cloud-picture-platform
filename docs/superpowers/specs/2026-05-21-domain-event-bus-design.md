# Domain Event Bus Design Spec

**Date:** 2026-05-21
**Status:** Approved
**Scope:** Full — infrastructure + migration + new event types

## Problem

Cross-aggregate collaboration currently uses direct service-to-service calls and scattered `ApplicationEventPublisher` usage. This violates DDD boundaries and creates tight coupling:

- `DeduplicationPictureUploadService` directly calls `notificationPublisher.notifyXxx()` (bypasses event abstraction)
- `TeamCommandService` directly calls `notificationPublisher.notifyTeamInvite()`
- Events (`PictureUploadedEvent`, `PictureReviewedEvent`) live in `application/picture/` instead of the domain layer
- No unified domain event infrastructure for future cross-aggregate needs

## Approach

**Spring-native Domain Events** — domain events as records in the domain layer, Spring `ApplicationEventPublisher` as the bus implementation behind a hexagonal port.

## Architecture

```
domain/events/
  DomainEvent.java              — marker interface (eventId, occurredAt defaults)
  DomainEventBus.java           — port interface (publish, publishAll)
  PictureUploadedEvent.java     — migrated from application layer
  PictureReviewedEvent.java     — migrated from application layer
  TeamInviteEvent.java          — new
  TeamMemberJoinedEvent.java    — new

infrastructure/events/
  SpringDomainEventBus.java     — @Primary adapter, wraps ApplicationEventPublisher

application/events/
  DomainEventSubscriber.java    — @TransactionalEventListener for all events
```

### Data Flow

```
Service (application layer)
  → domainEventBus.publish(event)
    → Spring ApplicationEventPublisher
      → DomainEventSubscriber (@TransactionalEventListener AFTER_COMMIT)
        → OutboxService.writeEvent()  → OutboxProcessor → NotificationPublisher
        → SearchIndexService.enqueuePicture()
```

## Domain Event Interface

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

Each event record provides defaults via compact constructor:

```java
public record PictureReviewedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID pictureId,
        String pictureName,
        UUID ownerId,
        boolean approved,
        String reason
) implements DomainEvent {
    public PictureReviewedEvent(UUID pictureId, String pictureName, UUID ownerId, boolean approved, String reason) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(), pictureId, pictureName, ownerId, approved, reason);
    }
}
```

## DomainEventBus Port

```java
package com.cn.cloudpictureplatform.domain.events;

import java.util.List;

public interface DomainEventBus {
    void publish(DomainEvent event);
    void publishAll(List<? extends DomainEvent> events);
}
```

## SpringDomainEventBus Adapter

```java
package com.cn.cloudpictureplatform.infrastructure.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

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

## Event Types

### Existing (migrated)

| Event | Fields | Triggers |
|-------|--------|----------|
| `PictureUploadedEvent` | pictureId, pictureName, ownerId, spaceId, teamId, isPublic | Picture upload complete |
| `PictureReviewedEvent` | pictureId, pictureName, ownerId, approved, reason | Review decision (manual or AI) |

### New

| Event | Fields | Triggers |
|-------|--------|----------|
| `TeamInviteEvent` | teamId, teamName, inviteeUsername, inviterUsername | Team invite sent |
| `TeamMemberJoinedEvent` | teamId, teamName, userId, username | Invite accepted |

## DomainEventSubscriber

Single `@Component` handler for all events. Uses `@TransactionalEventListener(phase = AFTER_COMMIT)` to ensure events are processed only after the publishing transaction commits.

```java
@Component
@RequiredArgsConstructor
public class DomainEventSubscriber {
    private final OutboxService outboxService;
    private final SearchIndexService searchIndexService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureReviewed(PictureReviewedEvent event) { ... }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureUploaded(PictureUploadedEvent event) { ... }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamInvite(TeamInviteEvent event) { ... }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamMemberJoined(TeamMemberJoinedEvent event) { ... }
}
```

## Migration Changes

### ModerationService
- `eventPublisher.publishEvent(...)` → `domainEventBus.publish(...)`
- Replace `ApplicationEventPublisher` injection with `DomainEventBus`

### PictureUploadService
- `eventPublisher.publishEvent(...)` → `domainEventBus.publish(...)`
- Replace `ApplicationEventPublisher` injection with `DomainEventBus`

### DeduplicationPictureUploadService
- Remove direct `notificationPublisher.notifyXxx()` calls from `notifyUploadRelatedParties()`
- Replace with `domainEventBus.publish(new PictureUploadedEvent(...))`
- Remove `notificationPublisher`, `appUserRepository`, `teamMemberRepository` dependencies (moved to subscriber)

### TeamCommandService
- Remove direct `notificationPublisher.notifyTeamInvite()` call
- Replace with `domainEventBus.publish(new TeamInviteEvent(...))`
- Remove `notificationPublisher` dependency

### PictureEventHandler
- Delete — logic moves to `DomainEventSubscriber`

## Files to Create

| File | Package |
|------|---------|
| `DomainEvent.java` | `domain.events` |
| `DomainEventBus.java` | `domain.events` |
| `PictureUploadedEvent.java` | `domain.events` |
| `PictureReviewedEvent.java` | `domain.events` |
| `TeamInviteEvent.java` | `domain.events` |
| `TeamMemberJoinedEvent.java` | `domain.events` |
| `SpringDomainEventBus.java` | `infrastructure.events` |
| `DomainEventSubscriber.java` | `application.events` |

## Files to Modify

| File | Change |
|------|--------|
| `ModerationService.java` | Replace `ApplicationEventPublisher` with `DomainEventBus` |
| `PictureUploadService.java` | Replace `ApplicationEventPublisher` with `DomainEventBus` |
| `DeduplicationPictureUploadService.java` | Replace direct notification calls with event publishing |
| `TeamCommandService.java` | Replace direct notification call with event publishing |

## Files to Delete

| File | Reason |
|------|--------|
| `application/picture/PictureEventHandler.java` | Replaced by `DomainEventSubscriber` |
| `application/picture/PictureUploadedEvent.java` | Moved to `domain/events/` |
| `application/picture/PictureReviewedEvent.java` | Moved to `domain/events/` |

## What Stays Unchanged

- `OutboxService`, `OutboxProcessor`, `OutboxEvent` — reliable delivery to external systems
- `NotificationPublisher` — WebSocket notification delivery
- `SearchIndexService` interface — search indexing

## Non-Goals

- Event replay / event sourcing — not needed at current scale
- Cross-JVM event distribution — Spring events are in-process; Outbox handles cross-service
- Custom `@DomainEventHandler` annotation — `@TransactionalEventListener` is sufficient
