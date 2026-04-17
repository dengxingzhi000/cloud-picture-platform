package com.cn.cloudpictureplatform.websocket.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Personal notification delivered to a specific user's queue.
 */
@Data
@Builder
public class NotificationMessage {

    private String title;
    private String body;
    private NotificationKind kind;
    private UUID targetId;
    private Instant timestamp;

    public enum NotificationKind {
        PICTURE_APPROVED,
        PICTURE_REJECTED,
        REVIEW_PENDING,
        TEAM_INVITE,
        UPLOAD_COMPLETE,
        TEAM_PICTURE_UPLOADED
    }
}
