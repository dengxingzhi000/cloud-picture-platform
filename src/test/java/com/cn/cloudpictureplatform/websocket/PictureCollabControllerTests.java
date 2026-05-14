package com.cn.cloudpictureplatform.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cn.cloudpictureplatform.application.picture.PictureDocumentService;
import com.cn.cloudpictureplatform.application.shared.dto.PictureDocumentElementResponse;
import com.cn.cloudpictureplatform.websocket.dto.CollabMessage;
import com.cn.cloudpictureplatform.websocket.dto.PictureDocumentOperationPayload;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@ExtendWith(MockitoExtension.class)
class PictureCollabControllerTests {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PresenceService presenceService;

    @Mock
    private EditLockPort editLockService;

    @Mock
    private PictureDocumentService pictureDocumentService;

    @Captor
    private ArgumentCaptor<Object> messageCaptor;

    private PictureCollabController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        controller = new PictureCollabController(
                messagingTemplate,
                presenceService,
                editLockService,
                pictureDocumentService,
                objectMapper
        );
    }

    @Test
    void shouldPersistAndBroadcastAnnotationOperation() throws Exception {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PictureDocumentOperationPayload payload = new PictureDocumentOperationPayload();
        payload.setId("rect-1");
        payload.setType("rect");
        payload.setX(20.0);
        payload.setY(30.0);
        payload.setWidth(80.0);
        payload.setHeight(40.0);

        CollabMessage message = CollabMessage.builder()
                .type(CollabMessage.EventType.ELEMENT_ADD)
                .schemaVersion(PictureCollabController.EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .payload(objectMapper.valueToTree(payload))
                .build();

        when(editLockService.refreshLock(pictureId, userId))
                .thenReturn(PresenceSnapshot.LockInfo.builder()
                        .lockedByUserId(userId)
                        .lockedByUsername("alice")
                        .lockedAt(java.time.Instant.now())
                        .expiresAt(java.time.Instant.now().plusSeconds(300))
                        .build());

        when(pictureDocumentService.applyOperation(
                eq(pictureId),
                eq(userId),
                eq(CollabMessage.EventType.ELEMENT_ADD),
                any(PictureDocumentOperationPayload.class)
        )).thenReturn(new PictureDocumentService.AppliedOperation(
                3L,
                null,
                PictureDocumentElementResponse.builder()
                        .id("rect-1")
                        .type("rect")
                        .x(20.0)
                        .y(30.0)
                        .width(80.0)
                        .height(40.0)
                        .build()
        ));
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put("userId", userId.toString());
        accessor.getSessionAttributes().put("username", "alice");

        controller.handleAnnotation(pictureId, objectMapper.writeValueAsString(message), accessor);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/pictures/" + pictureId + "/collab"),
                messageCaptor.capture()
        );
        Object sent = messageCaptor.getValue();
        assertTrue(sent instanceof CollabMessage);
        CollabMessage outbound = (CollabMessage) sent;
        JsonNode payloadNode = outbound.getPayload();
        assertEquals(CollabMessage.EventType.ELEMENT_ADD, outbound.getType());
        assertEquals(PictureCollabController.EVENT_SCHEMA_VERSION, outbound.getSchemaVersion());
        assertEquals(3L, outbound.getVersion().longValue());
        assertEquals(userId, outbound.getUserId());
        assertEquals("alice", outbound.getUsername());
        assertNotNull(payloadNode);
        assertEquals("rect-1", payloadNode.path("id").asText());
        assertEquals("rect", payloadNode.path("type").asText());
    }

    @Test
    void shouldReleaseLocksAndBroadcastPresenceOnDisconnect() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(presenceService.getUserIdForSession("session-1")).thenReturn(userId);
        when(presenceService.getPictureIdForSession("session-1")).thenReturn(pictureId);
        when(presenceService.handleDisconnect("session-1")).thenReturn(pictureId);
        when(presenceService.hasActiveSession(pictureId, userId)).thenReturn(false);
        when(editLockService.releaseLockForSession(pictureId, userId, "session-1")).thenReturn(true);
        when(presenceService.getPresence(pictureId)).thenReturn(java.util.List.of());
        when(editLockService.getLockInfo(pictureId)).thenReturn(null);

        controller.handleDisconnect(new SessionDisconnectEvent(
                this,
                MessageBuilder.withPayload(new byte[0]).build(),
                "session-1",
                CloseStatus.NORMAL
        ));

        verify(editLockService).releaseLockForSession(pictureId, userId, "session-1");
        verify(messagingTemplate).convertAndSend(
                eq("/topic/pictures/" + pictureId + "/collab"),
                messageCaptor.capture()
        );
        Object sent = messageCaptor.getValue();
        assertTrue(sent instanceof CollabMessage);
        CollabMessage outbound = (CollabMessage) sent;
        assertEquals(CollabMessage.EventType.PRESENCE_UPDATE, outbound.getType());
        assertEquals(pictureId, outbound.getPictureId());
    }

    @Test
    void shouldPersistDocumentOperationWithLock() throws Exception {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PictureDocumentOperationPayload payload = new PictureDocumentOperationPayload();
        payload.setId("rect-1");
        payload.setType("rect");

        when(editLockService.refreshLock(pictureId, userId))
                .thenReturn(PresenceSnapshot.LockInfo.builder()
                        .lockedByUserId(userId)
                        .lockedByUsername("alice")
                        .lockedAt(java.time.Instant.now())
                        .expiresAt(java.time.Instant.now().plusSeconds(300))
                        .build());

        CollabMessage message = CollabMessage.builder()
                .type(CollabMessage.EventType.ELEMENT_UPDATE)
                .schemaVersion(PictureCollabController.EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .payload(objectMapper.valueToTree(payload))
                .build();

        when(pictureDocumentService.applyOperation(
                eq(pictureId),
                eq(userId),
                eq(CollabMessage.EventType.ELEMENT_UPDATE),
                any(PictureDocumentOperationPayload.class)
        )).thenReturn(new PictureDocumentService.AppliedOperation(
                7L,
                null,
                PictureDocumentElementResponse.builder()
                        .id("rect-1")
                        .type("rect")
                        .x(48.0)
                        .y(64.0)
                        .width(120.0)
                        .height(90.0)
                        .build()
        ));

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put("userId", userId.toString());
        accessor.getSessionAttributes().put("username", "alice");

        controller.handleAnnotation(pictureId, objectMapper.writeValueAsString(message), accessor);

        verify(pictureDocumentService).applyOperation(
                eq(pictureId),
                eq(userId),
                eq(CollabMessage.EventType.ELEMENT_UPDATE),
                any(PictureDocumentOperationPayload.class)
        );
        verify(messagingTemplate).convertAndSend(
                eq("/topic/pictures/" + pictureId + "/collab"),
                messageCaptor.capture()
        );
        Object sent = messageCaptor.getValue();
        assertTrue(sent instanceof CollabMessage);
        CollabMessage outbound = (CollabMessage) sent;
        JsonNode payloadNode = outbound.getPayload();
        assertEquals(CollabMessage.EventType.ELEMENT_UPDATE, outbound.getType());
        assertEquals(pictureId, outbound.getPictureId());
        assertEquals(userId, outbound.getUserId());
        assertEquals(7L, outbound.getVersion().longValue());
        assertNotNull(payloadNode);
        assertEquals("rect-1", payloadNode.path("id").asText());
        assertEquals("rect", payloadNode.path("type").asText());
    }
}
