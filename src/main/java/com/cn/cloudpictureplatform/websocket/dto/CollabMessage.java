package com.cn.cloudpictureplatform.websocket.dto;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * STOMP message payload for real-time collaboration events.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollabMessage {

    private EventType type;
    private String schemaVersion;
    private UUID pictureId;
    private UUID userId;
    private String username;
    private Instant timestamp;
    private Long version;
    /** Type-specific JSON payload for React/Vue clients. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private JsonNode payload;

    public static JsonNode payloadFrom(ObjectMapper objectMapper, Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    public enum EventType {
        USER_JOINED,
        USER_LEFT,
        PRESENCE_UPDATE,
        LOCK_ACQUIRED,
        LOCK_RELEASED,
        LOCK_DENIED,
        CURSOR_UPDATE,
        SELECTION_UPDATE,
        ELEMENT_ADD,
        ELEMENT_UPDATE,
        ELEMENT_REMOVE,
        VERSION_CONFLICT,
        REVIEW_DECISION,
        PICTURE_UPLOADED
    }
}
