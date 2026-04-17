package com.cn.cloudpictureplatform.websocket.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full presence snapshot for a picture: who is currently viewing/editing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresenceSnapshot {

    private UUID pictureId;
    private List<UserPresence> users;
    private LockInfo lock;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserPresence {
        private UUID userId;
        private String username;
        private Instant joinedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LockInfo {
        private UUID lockedByUserId;
        private String lockedByUsername;
        private Instant lockedAt;
        private Instant expiresAt;
    }
}