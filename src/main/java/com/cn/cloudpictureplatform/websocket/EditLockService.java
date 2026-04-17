package com.cn.cloudpictureplatform.websocket;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;

/**
 * In-memory edit-lock manager.
 * A lock is granted to the first user who requests it and expires after {@link #LOCK_TTL_SECONDS}.
 * Expired locks are reclaimed by a scheduled cleanup task.
 */
@Service
public class EditLockService {
    static final long LOCK_TTL_SECONDS = 300; // 5 minutes

    private final Map<UUID, LockEntry> locks = new ConcurrentHashMap<>();

    /**
     * Try to acquire the lock on a picture.
     * @return true if the lock was granted (or already held by this user), false if held by someone else.
     */
    public boolean tryLock(UUID pictureId, UUID userId, String username, String sessionId) {
        Instant now = Instant.now();
        LockEntry existing = locks.get(pictureId);

        if (existing != null && !existing.isExpired(now)) {
            if (existing.userId().equals(userId)) {
                locks.put(pictureId, existing.refresh(now, sessionId));
                return true;
            }
            return false;
        }

        locks.put(pictureId, new LockEntry(pictureId, userId, username, sessionId, now,
                now.plusSeconds(LOCK_TTL_SECONDS)));
        return true;
    }

    /**
     * Release the lock if it is held by the given user.
     * @return true if the lock was released, false if it was held by someone else.
     */
    public boolean releaseLock(UUID pictureId, UUID userId) {
        LockEntry existing = locks.get(pictureId);
        if (existing == null || !existing.userId().equals(userId)) {
            return false;
        }
        locks.remove(pictureId);
        return true;
    }

    /** Release all locks held by this user (e.g. on disconnect). */
    public Set<UUID> releaseAll(UUID userId) {
        Set<UUID> pictureIds = locks.entrySet().stream()
                .filter(entry -> entry.getValue().userId().equals(userId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        locks.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
        return pictureIds;
    }

    public PresenceSnapshot.LockInfo getLockInfo(UUID pictureId) {
        LockEntry entry = locks.get(pictureId);
        if (entry == null || entry.isExpired(Instant.now())) {
            locks.remove(pictureId); // clean up stale
            return null;
        }
        return PresenceSnapshot.LockInfo.builder()
                .lockedByUserId(entry.userId())
                .lockedByUsername(entry.username())
                .lockedAt(entry.lockedAt())
                .expiresAt(entry.expiresAt())
                .build();
    }

    public long getLockTtlSeconds() {
        return LOCK_TTL_SECONDS;
    }

    /**
     * Refresh the lock TTL when the active holder is still editing.
     * @return current lock info when the caller holds the lock, otherwise null.
     */
    public PresenceSnapshot.LockInfo refreshLock(UUID pictureId, UUID userId) {
        Instant now = Instant.now();
        LockEntry entry = locks.get(pictureId);
        if (entry == null || entry.isExpired(now)) {
            locks.remove(pictureId);
            return null;
        }
        if (!entry.userId().equals(userId)) {
            return null;
        }
        LockEntry refreshed = entry.refresh(now, entry.sessionId());
        locks.put(pictureId, refreshed);
        return toLockInfo(refreshed);
    }

    /** Release the lock if it is still bound to the disconnecting session. */
    public boolean releaseLockForSession(UUID pictureId, UUID userId, String sessionId) {
        LockEntry existing = locks.get(pictureId);
        if (existing == null || !existing.userId().equals(userId)) {
            return false;
        }
        if (sessionId != null && existing.sessionId() != null && !existing.sessionId().equals(sessionId)) {
            return false;
        }
        locks.remove(pictureId);
        return true;
    }

    /** Evict expired locks and return the affected picture ids. */
    public Set<UUID> evictExpiredLocks() {
        Instant now = Instant.now();
        Set<UUID> expiredPictureIds = new LinkedHashSet<>();
        locks.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                expiredPictureIds.add(entry.getKey());
            }
            return expired;
        });
        return expiredPictureIds;
    }

    private PresenceSnapshot.LockInfo toLockInfo(LockEntry entry) {
        return PresenceSnapshot.LockInfo.builder()
                .lockedByUserId(entry.userId())
                .lockedByUsername(entry.username())
                .lockedAt(entry.lockedAt())
                .expiresAt(entry.expiresAt())
                .build();
    }

    private record LockEntry(
            UUID pictureId,
            UUID userId,
            String username,
            String sessionId,
            Instant lockedAt,
            Instant expiresAt
    ) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }

        LockEntry refresh(Instant now, String nextSessionId) {
            return new LockEntry(
                    pictureId,
                    userId,
                    username,
                    nextSessionId,
                    lockedAt,
                    now.plusSeconds(LOCK_TTL_SECONDS)
            );
        }
    }
}
