package com.cn.cloudpictureplatform.websocket;

import com.cn.cloudpictureplatform.application.picture.PictureDocumentService;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureEditorDocumentResponse;
import com.cn.cloudpictureplatform.websocket.dto.CollabMessage;
import com.cn.cloudpictureplatform.websocket.dto.EditorCursorPayload;
import com.cn.cloudpictureplatform.websocket.dto.EditorSelectionPayload;
import com.cn.cloudpictureplatform.websocket.dto.PictureDocumentOperationPayload;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * STOMP message handlers for real-time picture collaboration.
 *
 * <p>Client workflow:
 * <ol>
 *   <li>Connect to {@code /ws} with {@code Authorization: Bearer <jwt>} STOMP header.</li>
 *   <li>Subscribe to {@code /topic/pictures/{id}/collab} to receive collab events.</li>
 *   <li>Send to {@code /app/pictures/{id}/join} to announce presence.</li>
 *   <li>Send to {@code /app/pictures/{id}/lock} to request an edit lock.</li>
 *   <li>Send to {@code /app/pictures/{id}/unlock} to release the edit lock.</li>
 *   <li>Send to {@code /app/pictures/{id}/leave} or disconnect to leave.</li>
 * </ol>
 */
@Slf4j
@Controller
public class PictureCollabController {
    public static final String EVENT_SCHEMA_VERSION = "picture-editor-event.v1";

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final EditLockService editLockService;
    private final PictureDocumentService pictureDocumentService;
    private final ObjectMapper objectMapper;

    public PictureCollabController(
            SimpMessagingTemplate messagingTemplate,
            PresenceService presenceService,
            EditLockService editLockService,
            PictureDocumentService pictureDocumentService,
            ObjectMapper objectMapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
        this.editLockService = editLockService;
        this.pictureDocumentService = pictureDocumentService;
        this.objectMapper = objectMapper;
    }

    /**
     * User joins the collaboration session for a picture.
     * Broadcasts presence update to all subscribers of that picture's topic.
     */
    @MessageMapping("/pictures/{pictureId}/join")
    public void join(
            @DestinationVariable UUID pictureId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = extractUserId(headerAccessor);
        String username = extractUsername(headerAccessor);
        if (userId == null) return;

        String sessionId = headerAccessor.getSessionId();
        presenceService.join(pictureId, sessionId, userId, username);

        CollabMessage joined = CollabMessage.builder()
                .type(CollabMessage.EventType.USER_JOINED)
                .schemaVersion(EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .userId(userId)
                .username(username)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend(collabTopic(pictureId), joined);

        broadcastPresenceUpdate(pictureId);
    }

    /**
     * User explicitly leaves the collaboration session.
     */
    @MessageMapping("/pictures/{pictureId}/leave")
    public void leave(
            @DestinationVariable UUID pictureId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = extractUserId(headerAccessor);
        String username = extractUsername(headerAccessor);
        if (userId == null) return;

        String sessionId = headerAccessor.getSessionId();
        presenceService.leave(pictureId, sessionId);
        if (!presenceService.hasActiveSession(pictureId, userId)) {
            editLockService.releaseLockForSession(pictureId, userId, sessionId);
        }

        CollabMessage left = CollabMessage.builder()
                .type(CollabMessage.EventType.USER_LEFT)
                .schemaVersion(EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .userId(userId)
                .username(username)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend(collabTopic(pictureId), left);

        broadcastPresenceUpdate(pictureId);
    }

    /**
     * User requests an edit lock on a picture.
     * If the lock is granted, all subscribers are notified.
     * If denied (another user holds it), only the requester is notified via personal queue.
     */
    @MessageMapping("/pictures/{pictureId}/lock")
    public void requestLock(
            @DestinationVariable UUID pictureId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = extractUserId(headerAccessor);
        String username = extractUsername(headerAccessor);
        if (userId == null) return;

        boolean granted = editLockService.tryLock(pictureId, userId, username, headerAccessor.getSessionId());

        if (granted) {
            PresenceSnapshot.LockInfo lockInfo = editLockService.getLockInfo(pictureId);
            CollabMessage lockAcquired = CollabMessage.builder()
                    .type(CollabMessage.EventType.LOCK_ACQUIRED)
                    .schemaVersion(EVENT_SCHEMA_VERSION)
                    .pictureId(pictureId)
                    .userId(userId)
                    .username(username)
                    .timestamp(Instant.now())
                    .payload(CollabMessage.payloadFrom(objectMapper, lockInfo))
                    .build();
            messagingTemplate.convertAndSend(collabTopic(pictureId), lockAcquired);
        } else {
            PresenceSnapshot.LockInfo lockInfo = editLockService.getLockInfo(pictureId);
            CollabMessage lockDenied = CollabMessage.builder()
                    .type(CollabMessage.EventType.LOCK_DENIED)
                    .schemaVersion(EVENT_SCHEMA_VERSION)
                    .pictureId(pictureId)
                    .userId(userId)
                    .username(username)
                    .timestamp(Instant.now())
                    .payload(CollabMessage.payloadFrom(objectMapper, lockInfo))
                    .build();
            // Send only to the requester's personal queue
            messagingTemplate.convertAndSendToUser(username, "/queue/collab", lockDenied);
        }
    }

    /**
     * User releases the edit lock.
     */
    @MessageMapping("/pictures/{pictureId}/unlock")
    public void releaseLock(
            @DestinationVariable UUID pictureId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = extractUserId(headerAccessor);
        String username = extractUsername(headerAccessor);
        if (userId == null) return;

        boolean released = editLockService.releaseLock(pictureId, userId);
        if (released) {
            CollabMessage lockReleased = CollabMessage.builder()
                    .type(CollabMessage.EventType.LOCK_RELEASED)
                    .schemaVersion(EVENT_SCHEMA_VERSION)
                    .pictureId(pictureId)
                    .userId(userId)
                    .username(username)
                    .timestamp(Instant.now())
                    .build();
            messagingTemplate.convertAndSend(collabTopic(pictureId), lockReleased);
        }
    }

    /**
     * Refresh the current edit lock so active editors can prevent a stale timeout while idle.
     */
    @MessageMapping("/pictures/{pictureId}/lock/refresh")
    public void refreshLock(
            @DestinationVariable UUID pictureId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = extractUserId(headerAccessor);
        String username = extractUsername(headerAccessor);
        if (userId == null) {
            return;
        }

        PresenceSnapshot.LockInfo lockInfo = editLockService.refreshLock(pictureId, userId);
        if (lockInfo == null) {
            return;
        }

        CollabMessage refreshed = CollabMessage.builder()
                .type(CollabMessage.EventType.LOCK_ACQUIRED)
                .schemaVersion(EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .userId(userId)
                .username(username)
                .timestamp(Instant.now())
                .payload(CollabMessage.payloadFrom(objectMapper, lockInfo))
                .build();
        messagingTemplate.convertAndSend(collabTopic(pictureId), refreshed);
        broadcastPresenceUpdate(pictureId);
    }

    /**
     * On subscribe to the topic, immediately send the current snapshot to the subscriber.
     */
    @SubscribeMapping("/pictures/{pictureId}/collab")
    public PresenceSnapshot onSubscribe(@DestinationVariable UUID pictureId) {
        List<PresenceSnapshot.UserPresence> users = presenceService.getPresence(pictureId);
        PresenceSnapshot.LockInfo lockInfo = editLockService.getLockInfo(pictureId);
        return PresenceSnapshot.builder()
                .pictureId(pictureId)
                .users(users)
                .lock(lockInfo)
                .build();
    }

    /**
     * Simple annotation messages from clients are broadcast to the picture topic.
     * Clients send validated document operations to /app/pictures/{pictureId}/annotation.
     */
    @MessageMapping("/pictures/{pictureId}/annotation")
    public void handleAnnotation(
            @DestinationVariable UUID pictureId,
            String rawPayload,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = extractUserId(headerAccessor);
        String username = extractUsername(headerAccessor);
        if (userId == null) {
            return;
        }

        CollabMessage incoming = parseMessage(rawPayload);
        if (incoming.getType() == CollabMessage.EventType.CURSOR_UPDATE
                || incoming.getType() == CollabMessage.EventType.SELECTION_UPDATE) {
            Object normalizedPayload = incoming.getType() == CollabMessage.EventType.CURSOR_UPDATE
                    ? validateCursorPayload(incoming)
                    : validateSelectionPayload(incoming);
            CollabMessage outbound = CollabMessage.builder()
                    .type(incoming.getType())
                    .schemaVersion(EVENT_SCHEMA_VERSION)
                    .pictureId(pictureId)
                    .userId(userId)
                    .username(username)
                    .timestamp(Instant.now())
                    .payload(CollabMessage.payloadFrom(objectMapper, normalizedPayload))
                    .build();
            messagingTemplate.convertAndSend(collabTopic(pictureId), outbound);
            return;
        }

        PictureDocumentOperationPayload payload = objectMapper.convertValue(
                incoming.getPayload(),
                PictureDocumentOperationPayload.class
        );
        editLockService.refreshLock(pictureId, userId);
        try {
            PictureDocumentService.AppliedOperation applied = pictureDocumentService.applyOperation(
                    pictureId,
                    userId,
                    incoming.getType(),
                    payload
            );
            CollabMessage outbound = CollabMessage.builder()
                    .type(incoming.getType())
                    .schemaVersion(EVENT_SCHEMA_VERSION)
                    .pictureId(pictureId)
                    .userId(userId)
                    .username(username)
                    .timestamp(Instant.now())
                    .version(applied.version())
                    .payload(CollabMessage.payloadFrom(objectMapper, applied.element()))
                    .build();
            messagingTemplate.convertAndSend(collabTopic(pictureId), outbound);
        } catch (ApiException exception) {
            if (exception.getErrorCode() != ApiErrorCode.CONFLICT) {
                throw exception;
            }
            sendVersionConflict(username, pictureId, userId);
        }
    }

    /**
     * Clean up presence and edit lock when a WebSocket session disconnects.
     */

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        UUID userId = presenceService.getUserIdForSession(sessionId);
        UUID trackedPictureId = presenceService.getPictureIdForSession(sessionId);
        Set<UUID> affectedPictureIds = new LinkedHashSet<>();
        UUID pictureId = presenceService.handleDisconnect(sessionId);
        if (pictureId != null) {
            affectedPictureIds.add(pictureId);
        }
        UUID resolvedPictureId = pictureId != null ? pictureId : trackedPictureId;
        if (userId != null && resolvedPictureId != null && !presenceService.hasActiveSession(resolvedPictureId, userId)) {
            boolean released = editLockService.releaseLockForSession(resolvedPictureId, userId, sessionId);
            if (released) {
                affectedPictureIds.add(resolvedPictureId);
            }
        }
        for (UUID affectedPictureId : affectedPictureIds) {
            broadcastPresenceUpdate(affectedPictureId);
        }
    }

    /**
     * Broadcast lock release and presence updates when stale locks are reclaimed by TTL cleanup.
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredLocks() {
        for (UUID pictureId : editLockService.evictExpiredLocks()) {
            CollabMessage lockReleased = CollabMessage.builder()
                    .type(CollabMessage.EventType.LOCK_RELEASED)
                    .schemaVersion(EVENT_SCHEMA_VERSION)
                    .pictureId(pictureId)
                    .timestamp(Instant.now())
                    .build();
            messagingTemplate.convertAndSend(collabTopic(pictureId), lockReleased);
            broadcastPresenceUpdate(pictureId);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void broadcastPresenceUpdate(UUID pictureId) {
        List<PresenceSnapshot.UserPresence> users = presenceService.getPresence(pictureId);
        PresenceSnapshot.LockInfo lockInfo = editLockService.getLockInfo(pictureId);
        PresenceSnapshot snapshot = PresenceSnapshot.builder()
                .pictureId(pictureId)
                .users(users)
                .lock(lockInfo)
                .build();
        CollabMessage update = CollabMessage.builder()
                .type(CollabMessage.EventType.PRESENCE_UPDATE)
                .schemaVersion(EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .timestamp(Instant.now())
                .payload(CollabMessage.payloadFrom(objectMapper, snapshot))
                .build();
        messagingTemplate.convertAndSend(collabTopic(pictureId), update);
    }

    private static String collabTopic(UUID pictureId) {
        return "/topic/pictures/" + pictureId + "/collab";
    }

    private void sendVersionConflict(String username, UUID pictureId, UUID userId) {
        PictureEditorDocumentResponse snapshot = pictureDocumentService.getSnapshot(pictureId);
        CollabMessage conflict = CollabMessage.builder()
                .type(CollabMessage.EventType.VERSION_CONFLICT)
                .schemaVersion(EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .userId(userId)
                .username(username)
                .timestamp(Instant.now())
                .version(snapshot.version())
                .payload(CollabMessage.payloadFrom(objectMapper, snapshot))
                .build();
        messagingTemplate.convertAndSendToUser(username, "/queue/collab", conflict);
    }

    private CollabMessage parseMessage(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, CollabMessage.class);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid collaboration payload");
        }
    }

    private EditorCursorPayload validateCursorPayload(CollabMessage incoming) {
        EditorCursorPayload payload = objectMapper.convertValue(incoming.getPayload(), EditorCursorPayload.class);
        if (payload.getX() == null || payload.getY() == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "cursor x and y are required");
        }
        return payload;
    }

    private EditorSelectionPayload validateSelectionPayload(CollabMessage incoming) {
        EditorSelectionPayload payload = objectMapper.convertValue(incoming.getPayload(), EditorSelectionPayload.class);
        if ((payload.getElementIds() == null || payload.getElementIds().isEmpty())
                && (payload.getActiveElementId() == null || payload.getActiveElementId().isBlank())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "selection target is required");
        }
        return payload;
    }

    private static UUID extractUserId(SimpMessageHeaderAccessor accessor) {
        Object uid = accessor.getSessionAttributes() != null
                ? accessor.getSessionAttributes().get("userId")
                : null;
        if (uid instanceof UUID u) return u;
        if (uid instanceof String s) return UUID.fromString(s);
        return null;
    }

    private static String extractUsername(SimpMessageHeaderAccessor accessor) {
        if (accessor.getSessionAttributes() != null) {
            Object u = accessor.getSessionAttributes().get("username");
            if (u instanceof String s) return s;
        }
        return accessor.getUser() != null ? accessor.getUser().getName() : "unknown";
    }
}
