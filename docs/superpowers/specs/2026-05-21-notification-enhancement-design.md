# Notification System Enhancement Spec

**Date:** 2026-05-21
**Status:** Approved
**Scope:** Backend persistence + Frontend notification improvements

## Problem

1. Backend sends `TEAM_MEMBER_JOINED` notifications but frontend doesn't handle this kind
2. No notification persistence — notifications lost on page refresh
3. Bell auto-marks all as read on click (UX bug)
4. Duplicate `Notificationcenter.tsx` (unused)
5. No unread badge on Teams nav item

## Backend Changes

### New Entity: NotificationRecord

```
domain/notification/NotificationRecord.java
  - id (UUID, PK)
  - userId (UUID, not null)
  - kind (String, not null) — matches NotificationKind enum values
  - title (String, not null)
  - body (String)
  - targetId (UUID)
  - isRead (boolean, default false)
  - createdAt (Instant, not null)
```

### New Repository

```
infrastructure/persistence/NotificationRecordRepository.java
  - findByUserIdOrderByCreatedAtDesc(userId, pageable) → Page<NotificationRecord>
  - countByUserIdAndIsReadFalse(userId) → long
  - @Modifying markAllAsReadByUserId(userId)
  - @Modifying markAsRead(id, userId)
```

### New Service

```
application/notification/NotificationService.java
  - save(notification) — called by DomainEventSubscriber
  - listByUser(userId, page, size) → Page<NotificationRecord>
  - countUnread(userId) → long
  - markRead(id, userId)
  - markAllRead(userId)
```

### New Controller

```
interfaces/notification/NotificationController.java
  GET    /api/notifications              → list (paginated)
  GET    /api/notifications/unread-count → { count: number }
  PATCH  /api/notifications/{id}/read    → mark single as read
  PATCH  /api/notifications/read-all     → mark all as read
```

### Flyway Migration

```sql
-- V13__create_notification_record.sql
CREATE TABLE notification_record (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    kind VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT,
    target_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_user_created ON notification_record(user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification_record(user_id, is_read) WHERE is_read = FALSE;
```

### DomainEventSubscriber Update

In each event handler, after writing to outbox, also save to notification_record:
```java
notificationService.save(NotificationRecord.builder()
    .userId(targetUserId)
    .kind("TEAM_INVITE")
    .title("Team invitation")
    .body(inviterUsername + " invited you to join team \"" + teamName + "\".")
    .targetId(teamId)
    .build());
```

## Frontend Changes

### 1. Type Update

Add `TEAM_MEMBER_JOINED` to `NotificationKind` in:
- `src/react-app/pages/notifications.tsx`
- `src/collab-types.d.ts`

### 2. Display Mappings

In `NotificationBell.tsx`:
- `TEAM_MEMBER_JOINED` → 👥 emoji, routes to `/teams/{targetId}`

In `notifications.tsx`:
- `TEAM_MEMBER_JOINED` → `info` toast kind

### 3. Fix Auto-Mark-Read

Remove `markAllRead()` from bell toggle. Only mark individual notification as read when clicked.

### 4. Delete Notificationcenter.tsx

Remove unused duplicate component.

### 5. Notification Persistence Integration

In `NotificationProvider`:
- On auth, fetch `/api/notifications?size=50` to populate initial list
- When WebSocket notification arrives, also persist via API (or rely on backend saving)

Add API function: `getNotifications(page, size)`, `getUnreadCount()`, `markNotificationRead(id)`, `markAllNotificationsRead()`

### 6. Unread Badge on Teams Nav

In App.tsx header, show unread notification count badge on Teams nav link.
Fetch `/api/notifications/unread-count` on auth and on new notification.
