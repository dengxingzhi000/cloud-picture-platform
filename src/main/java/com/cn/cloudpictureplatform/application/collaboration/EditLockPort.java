package com.cn.cloudpictureplatform.application.collaboration;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;

public interface EditLockPort {
    boolean tryLock(UUID pictureId, UUID userId, String username, String sessionId, Duration ttl);
    boolean releaseLock(UUID pictureId, UUID userId);
    boolean releaseLockForSession(UUID pictureId, UUID userId, String sessionId);
    PresenceSnapshot.LockInfo refreshLock(UUID pictureId, UUID userId);
    PresenceSnapshot.LockInfo getLockInfo(UUID pictureId);
    long getLockTtlSeconds();
    Set<UUID> evictExpiredLocks();
}
