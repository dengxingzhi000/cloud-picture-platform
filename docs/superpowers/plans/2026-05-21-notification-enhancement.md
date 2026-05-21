# Notification System Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add notification persistence to backend, wire up TEAM_MEMBER_JOINED in frontend, fix notification UX bugs.

**Architecture:** Backend: NotificationRecord JPA entity + repository + service + REST API. Frontend: type updates, display mappings, persistence integration, unread badge.

**Tech Stack:** Java 21, Spring Boot 4.0.5, JPA, Flyway, React 19, TypeScript, STOMP/SockJS

**Spec:** `docs/superpowers/specs/2026-05-21-notification-enhancement-design.md`

---

## Backend Tasks

### Task 1: Create NotificationRecord entity + migration

**Files:**
- Create: `src/main/resources/db/migration/V13__create_notification_record.sql`
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/notification/NotificationRecord.java`

- [ ] **Step 1: Create Flyway migration**

```sql
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

- [ ] **Step 2: Create entity**

```java
package com.cn.cloudpictureplatform.domain.notification;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "target_id", columnDefinition = "uuid")
    private UUID targetId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V13__create_notification_record.sql
git add src/main/java/com/cn/cloudpictureplatform/domain/notification/NotificationRecord.java
git commit -m "feat: add NotificationRecord entity and migration"
```

---

### Task 2: Create NotificationRecordRepository

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/NotificationRecordRepository.java`

- [ ] **Step 1: Create repository**

```java
package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.notification.NotificationRecord;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, UUID> {

    Page<NotificationRecord> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE NotificationRecord n SET n.read = true WHERE n.id = :id AND n.userId = :userId")
    int markAsRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE NotificationRecord n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllAsReadByUserId(@Param("userId") UUID userId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/NotificationRecordRepository.java
git commit -m "feat: add NotificationRecordRepository"
```

---

### Task 3: Create NotificationService

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/application/notification/NotificationService.java`

- [ ] **Step 1: Create service**

```java
package com.cn.cloudpictureplatform.application.notification;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.domain.notification.NotificationRecord;
import com.cn.cloudpictureplatform.infrastructure.persistence.NotificationRecordRepository;

@Service
public class NotificationService {

    private final NotificationRecordRepository repository;

    public NotificationService(NotificationRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationRecord save(UUID userId, String kind, String title, String body, UUID targetId) {
        NotificationRecord record = NotificationRecord.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .kind(kind)
                .title(title)
                .body(body)
                .targetId(targetId)
                .read(false)
                .createdAt(Instant.now())
                .build();
        return repository.save(record);
    }

    @Transactional(readOnly = true)
    public Page<NotificationRecord> listByUser(UUID userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public boolean markRead(UUID id, UUID userId) {
        return repository.markAsRead(id, userId) > 0;
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllAsReadByUserId(userId);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/notification/NotificationService.java
git commit -m "feat: add NotificationService"
```

---

### Task 4: Create NotificationController

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/interfaces/notification/NotificationController.java`

- [ ] **Step 1: Create controller**

```java
package com.cn.cloudpictureplatform.interfaces.notification;

import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.notification.NotificationService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.notification.NotificationRecord;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AppUserRepository appUserRepository;

    public NotificationController(NotificationService notificationService, AppUserRepository appUserRepository) {
        this.notificationService = notificationService;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<NotificationRecord> result = notificationService.listByUser(userId, page, size);
        var items = result.getContent().stream().map(this::toResponse).toList();
        return ApiResponse.ok(new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@RequestParam UUID userId) {
        return ApiResponse.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id, @RequestParam UUID userId) {
        notificationService.markRead(id, userId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@RequestParam UUID userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.ok(null);
    }

    private NotificationResponse toResponse(NotificationRecord record) {
        return new NotificationResponse(
                record.getId(), record.getKind(), record.getTitle(),
                record.getBody(), record.getTargetId(),
                record.isRead(), record.getCreatedAt()
        );
    }
}
```

- [ ] **Step 2: Create NotificationResponse DTO**

```java
package com.cn.cloudpictureplatform.interfaces.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String kind,
        String title,
        String body,
        UUID targetId,
        boolean read,
        Instant createdAt
) {}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/interfaces/notification/
git commit -m "feat: add NotificationController with REST API"
```

---

### Task 5: Integrate notification persistence into DomainEventSubscriber

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/events/DomainEventSubscriber.java`

- [ ] **Step 1: Add NotificationService dependency**

Add field: `private final NotificationService notificationService;`
Add to constructor.

- [ ] **Step 2: Save notifications in each handler**

In `onPictureReviewed()`:
```java
AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
if (owner != null) {
    notificationService.save(owner.getId(), 
            event.approved() ? "PICTURE_APPROVED" : "PICTURE_REJECTED",
            event.approved() ? "Picture approved" : "Picture rejected",
            "Your picture \"" + event.pictureName() + "\" was " + (event.approved() ? "approved." : "rejected."),
            event.pictureId());
}
```

In `onPictureUploaded()`:
```java
if (owner != null) {
    notificationService.save(owner.getId(), "UPLOAD_COMPLETE",
            "Picture upload completed",
            "Your picture \"" + event.pictureName() + "\" is available now.",
            event.pictureId());
}
```

In `onTeamInvite()`:
```java
AppUser invitee = appUserRepository.findByUsername(event.inviteeUsername()).orElse(null);
if (invitee != null) {
    notificationService.save(invitee.getId(), "TEAM_INVITE",
            "Team invitation",
            event.inviterUsername() + " invited you to join team \"" + event.teamName() + "\".",
            event.teamId());
}
```

In `onTeamMemberJoined()`:
```java
// Notify all active team members except the one who joined
List<TeamMember> members = teamMemberRepository.findByTeamIdAndStatus(event.teamId(), TeamMemberStatus.ACTIVE);
for (TeamMember member : members) {
    if (!member.getUserId().equals(event.userId())) {
        notificationService.save(member.getUserId(), "TEAM_MEMBER_JOINED",
                "New team member",
                event.username() + " joined team \"" + event.teamName() + "\".",
                event.teamId());
    }
}
```

- [ ] **Step 3: Compile**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/events/DomainEventSubscriber.java
git commit -m "feat: integrate notification persistence into DomainEventSubscriber"
```

---

## Frontend Tasks

### Task 6: Add notification API functions

**Files:**
- Create or modify: `src/api/notifications.ts` (in frontend project)

Working directory for frontend: `D:\ProgramProject\PolymerizationProject\cloud-picture-platform-web`

- [ ] **Step 1: Create notification API**

```typescript
import client from './client';

export interface NotificationItem {
  id: string;
  kind: string;
  title: string;
  body: string | null;
  targetId: string | null;
  read: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export async function getNotifications(page = 0, size = 50) {
  const res = await client.get<PageResponse<NotificationItem>>('/api/notifications', {
    params: { page, size },
  });
  return res.data;
}

export async function getUnreadCount() {
  const res = await client.get<{ count: number }>('/api/notifications/unread-count');
  return res.data.count;
}

export async function markNotificationRead(id: string) {
  await client.patch(`/api/notifications/${id}/read`);
}

export async function markAllNotificationsRead() {
  await client.patch('/api/notifications/read-all');
}
```

- [ ] **Step 2: Commit**

```bash
git add src/api/notifications.ts
git commit -m "feat: add notification API functions"
```

---

### Task 7: Update NotificationKind type

**Files:**
- Modify: `src/react-app/pages/notifications.tsx`
- Modify: `src/collab-types.d.ts`

- [ ] **Step 1: Add TEAM_MEMBER_JOINED to type**

In both files, add `'TEAM_MEMBER_JOINED'` to the `NotificationKind` type union.

In `notifications.tsx`, add to `KIND_TOAST`:
```typescript
TEAM_MEMBER_JOINED: 'info',
```

- [ ] **Step 2: Commit**

```bash
git add src/react-app/pages/notifications.tsx src/collab-types.d.ts
git commit -m "feat: add TEAM_MEMBER_JOINED to NotificationKind type"
```

---

### Task 8: Update NotificationBell display mappings

**Files:**
- Modify: `src/react-app/pages/NotificationBell.tsx`

- [ ] **Step 1: Add TEAM_MEMBER_JOINED to KIND_EMOJI and KIND_ICON maps**

Add emoji: `👥`
Add icon: `UserPlus` from lucide-react
Add routing: navigate to `/teams/{targetId}`

- [ ] **Step 2: Fix auto-mark-read bug**

Remove `markAllRead()` from the bell toggle onClick. Instead, add `markNotificationRead(id)` when a specific notification is clicked.

- [ ] **Step 3: Commit**

```bash
git add src/react-app/pages/NotificationBell.tsx
git commit -m "feat: add TEAM_MEMBER_JOINED display + fix auto-mark-read bug"
```

---

### Task 9: Integrate notification persistence in NotificationProvider

**Files:**
- Modify: `src/react-app/pages/notifications.tsx`

- [ ] **Step 1: Fetch notifications on auth**

In the `NotificationProvider`, when user is authenticated, fetch `/api/notifications?size=50` to populate initial notification list.

- [ ] **Step 2: Sync WebSocket notifications with backend**

When a WebSocket notification arrives, it's already saved by the backend. Just prepend to the local list.

- [ ] **Step 3: Add markRead and markAllRead functions**

Expose `markRead(id)` and `markAllRead()` via context that call the API and update local state.

- [ ] **Step 4: Commit**

```bash
git add src/react-app/pages/notifications.tsx
git commit -m "feat: integrate notification persistence in NotificationProvider"
```

---

### Task 10: Add unread badge to Teams nav

**Files:**
- Modify: `src/react-app/App.tsx` (or wherever the nav is rendered)

- [ ] **Step 1: Add unread count state**

Fetch `/api/notifications/unread-count` on auth and on new notification.

- [ ] **Step 2: Show badge on Teams nav link**

If unreadCount > 0, show a small red badge on the Teams nav item.

- [ ] **Step 3: Commit**

```bash
git add src/react-app/App.tsx
git commit -m "feat: add unread notification badge on Teams nav"
```

---

### Task 11: Delete Notificationcenter.tsx

**Files:**
- Delete: `src/react-app/pages/Notificationcenter.tsx`

- [ ] **Step 1: Verify no references**

Search for imports of `Notificationcenter` in the codebase.

- [ ] **Step 2: Delete**

```bash
rm src/react-app/pages/Notificationcenter.tsx
```

- [ ] **Step 3: Commit**

```bash
git add src/react-app/pages/Notificationcenter.tsx
git commit -m "refactor: delete unused Notificationcenter.tsx"
```

---

### Task 12: Final verification

- [ ] **Step 1: Backend compile**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.jdks\graalvm-jdk-21.0.8"; .\mvnw compile`

- [ ] **Step 2: Frontend build**

Run: `npm run build` (in frontend project directory)

- [ ] **Step 3: Commit any remaining changes**

```bash
git add -A
git commit -m "feat: complete notification system enhancement"
```
