package com.cn.cloudpictureplatform.infrastructure.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.websocket.EditLockPort;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisEditLockAdapter implements EditLockPort {
    static final long LOCK_TTL_SECONDS = 300;
    private static final String KEY_PREFIX = "picture:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(LOCK_TTL_SECONDS);

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Boolean> compareAndDeleteScript;
    private DefaultRedisScript<Boolean> compareAndRefreshScript;

    public RedisEditLockAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void initScripts() {
        compareAndDeleteScript = new DefaultRedisScript<>();
        compareAndDeleteScript.setScriptText(
                "local val = redis.call('GET', KEYS[1]) " +
                "if val == ARGV[1] then " +
                "  return redis.call('DEL', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end"
        );
        compareAndDeleteScript.setResultType(Boolean.class);

        compareAndRefreshScript = new DefaultRedisScript<>();
        compareAndRefreshScript.setScriptText(
                "local val = redis.call('GET', KEYS[1]) " +
                "if val ~= nil and val ~= '' then " +
                "  local fields = redis.call('JSON.OBJKEYS', KEYS[1]) " +
                "  return 0 " +
                "end " +
                "return 0"
        );
        compareAndRefreshScript.setResultType(Boolean.class);
    }

    private String lockKey(UUID pictureId) {
        return KEY_PREFIX + pictureId;
    }

    @Override
    public boolean tryLock(UUID pictureId, UUID userId, String username, String sessionId, Duration ttl) {
        String key = lockKey(pictureId);
        String value = userId + ":" + sessionId;
        Duration expiry = ttl != null ? ttl : LOCK_TTL;
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, expiry);
        if (Boolean.TRUE.equals(result)) {
            log.debug("Lock acquired: picture={}, user={}", pictureId, userId);
            return true;
        }
        String current = redisTemplate.opsForValue().get(key);
        if (current != null && current.startsWith(userId + ":")) {
            redisTemplate.expire(key, expiry);
            log.debug("Lock refreshed (re-acquire): picture={}, user={}", pictureId, userId);
            return true;
        }
        return false;
    }

    @Override
    public boolean releaseLock(UUID pictureId, UUID userId) {
        String key = lockKey(pictureId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return false;
        if (!value.startsWith(userId + ":")) return false;
        redisTemplate.delete(key);
        log.debug("Lock released: picture={}, user={}", pictureId, userId);
        return true;
    }

    @Override
    public boolean releaseLockForSession(UUID pictureId, UUID userId, String sessionId) {
        String key = lockKey(pictureId);
        String expected = userId + ":" + (sessionId != null ? sessionId : "");
        String actual = redisTemplate.opsForValue().get(key);
        if (expected.equals(actual)) {
            redisTemplate.delete(key);
            log.debug("Lock released for session: picture={}, session={}", pictureId, sessionId);
            return true;
        }
        return false;
    }

    @Override
    public PresenceSnapshot.LockInfo refreshLock(UUID pictureId, UUID userId) {
        String key = lockKey(pictureId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        if (!value.startsWith(userId + ":")) return null;
        redisTemplate.expire(key, LOCK_TTL);
        Long ttl = redisTemplate.getExpire(key);
        long ttlSeconds = ttl != null ? ttl : LOCK_TTL_SECONDS;
        return PresenceSnapshot.LockInfo.builder()
                .lockedByUserId(userId)
                .lockedAt(Instant.now().minusSeconds(LOCK_TTL_SECONDS - ttlSeconds))
                .expiresAt(Instant.now().plusSeconds(ttlSeconds))
                .build();
    }

    @Override
    public PresenceSnapshot.LockInfo getLockInfo(UUID pictureId) {
        String key = lockKey(pictureId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl <= 0) {
            redisTemplate.delete(key);
            return null;
        }
        String[] parts = value.split(":", 2);
        UUID userId = UUID.fromString(parts[0]);
        return PresenceSnapshot.LockInfo.builder()
                .lockedByUserId(userId)
                .lockedAt(Instant.now().minusSeconds(LOCK_TTL_SECONDS - ttl))
                .expiresAt(Instant.now().plusSeconds(ttl))
                .build();
    }

    @Override
    public long getLockTtlSeconds() {
        return LOCK_TTL_SECONDS;
    }

    @Override
    public Set<UUID> evictExpiredLocks() {
        Set<UUID> expired = new LinkedHashSet<>();
        String pattern = KEY_PREFIX + "*";
        var keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) return expired;
        for (String key : keys) {
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl <= 0) {
                redisTemplate.delete(key);
                String pictureId = key.substring(KEY_PREFIX.length());
                expired.add(UUID.fromString(pictureId));
            }
        }
        if (!expired.isEmpty()) {
            log.debug("Evicted {} expired locks", expired.size());
        }
        return expired;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredLocks() {
        evictExpiredLocks();
    }
}
