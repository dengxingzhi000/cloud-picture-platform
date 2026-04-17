package com.cn.cloudpictureplatform.websocket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;

/**
 * In-memory tracking of which users are currently viewing/editing each picture.
 * State is ephemeral and resets on application restart (acceptable for P4).
 */
@Service
public class PresenceService {

    /** pictureId → set of session entries */
    private final Map<UUID, Set<SessionEntry>> picturePresence = new ConcurrentHashMap<>();
    /** sessionId → (pictureId, userId, username) — used for disconnect cleanup */
    private final Map<String, SessionEntry> sessionIndex = new ConcurrentHashMap<>();

    public void join(UUID pictureId, String sessionId, UUID userId, String username) {
        SessionEntry previous = sessionIndex.get(sessionId);
        if (previous != null && !previous.pictureId().equals(pictureId)) {
            leave(previous.pictureId(), sessionId);
        }
        SessionEntry entry = new SessionEntry(sessionId, pictureId, userId, username, Instant.now());
        picturePresence
                .computeIfAbsent(pictureId, k -> new CopyOnWriteArraySet<>())
                .add(entry);
        sessionIndex.put(sessionId, entry);
    }

    public void leave(UUID pictureId, String sessionId) {
        Set<SessionEntry> entries = picturePresence.get(pictureId);
        if (entries != null) {
            entries.removeIf(e -> e.sessionId().equals(sessionId));
            if (entries.isEmpty()) {
                picturePresence.remove(pictureId);
            }
        }
        sessionIndex.remove(sessionId);
    }

    /**
     * Called on WebSocket disconnect — removes the session from whichever picture it joined.
     * @return pictureId that was left, or null if session was not tracked
     */
    public UUID handleDisconnect(String sessionId) {
        SessionEntry entry = sessionIndex.remove(sessionId);
        if (entry == null) {
            return null;
        }
        leave(entry.pictureId(), sessionId);
        return entry.pictureId();
    }

    public List<PresenceSnapshot.UserPresence> getPresence(UUID pictureId) {
        Set<SessionEntry> entries = picturePresence.getOrDefault(pictureId, Set.of());
        Map<UUID, SessionEntry> byUser = new LinkedHashMap<>();
        for (SessionEntry e : entries) {
            SessionEntry existing = byUser.get(e.userId());
            if (existing == null || e.joinedAt().isBefore(existing.joinedAt())) {
                byUser.put(e.userId(), e);
            }
        }
        List<PresenceSnapshot.UserPresence> result = new ArrayList<>();
        byUser.values().stream()
                .sorted(Comparator.comparing(SessionEntry::joinedAt))
                .forEach(entry -> result.add(PresenceSnapshot.UserPresence.builder()
                        .userId(entry.userId())
                        .username(entry.username())
                        .joinedAt(entry.joinedAt())
                        .build()));
        return result;
    }

    /** Returns the userId that owns the given session, or null. */
    public UUID getUserIdForSession(String sessionId) {
        SessionEntry entry = sessionIndex.get(sessionId);
        return entry == null ? null : entry.userId();
    }

    /** Returns the username that owns the given session, or null. */
    public String getUsernameForSession(String sessionId) {
        SessionEntry entry = sessionIndex.get(sessionId);
        return entry == null ? null : entry.username();
    }

    /** Returns the picture currently associated with the session, or null. */
    public UUID getPictureIdForSession(String sessionId) {
        SessionEntry entry = sessionIndex.get(sessionId);
        return entry == null ? null : entry.pictureId();
    }

    /** Returns true when the user still has any active session in the picture. */
    public boolean hasActiveSession(UUID pictureId, UUID userId) {
        if (pictureId == null || userId == null) {
            return false;
        }
        return picturePresence.getOrDefault(pictureId, Set.of()).stream()
                .anyMatch(entry -> userId.equals(entry.userId()));
    }

    private record SessionEntry(
            String sessionId,
            UUID pictureId,
            UUID userId,
            String username,
            Instant joinedAt
    ) {}
}
