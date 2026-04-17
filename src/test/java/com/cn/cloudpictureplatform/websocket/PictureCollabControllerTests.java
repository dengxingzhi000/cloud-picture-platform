package com.cn.cloudpictureplatform.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cn.cloudpictureplatform.application.picture.PictureDocumentService;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureDocumentElementResponse;
import com.cn.cloudpictureplatform.websocket.dto.CollabMessage;
import com.cn.cloudpictureplatform.websocket.dto.PictureDocumentOperationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private EditLockService editLockService;

    @Mock
    private PictureDocumentService pictureDocumentService;

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
                .payload(payload)
                .build();

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
                argThat(argument -> {
                    if (!(argument instanceof CollabMessage outbound)) {
                        return false;
                    }
                    JsonNode payloadNode = outbound.getPayload();
                    return outbound.getType() == CollabMessage.EventType.ELEMENT_ADD
                            && PictureCollabController.EVENT_SCHEMA_VERSION.equals(outbound.getSchemaVersion())
                            && outbound.getVersion() == 3L
                            && userId.equals(outbound.getUserId())
                            && "alice".equals(outbound.getUsername())
                            && payloadNode != null
                            && "rect-1".equals(payloadNode.path("id").asText())
                            && "rect".equals(payloadNode.path("type").asText());
                })
        );
    }

    @Test
    void shouldReleaseLocksAndBroadcastPresenceOnDisconnect() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(presenceService.getUserIdForSession("session-1")).thenReturn(userId);
        when(editLockService.releaseAll(userId)).thenReturn(Set.of(pictureId));
        when(presenceService.handleDisconnect("session-1")).thenReturn(pictureId);
        when(presenceService.getPresence(pictureId)).thenReturn(java.util.List.of());
        when(editLockService.getLockInfo(pictureId)).thenReturn(null);

        controller.handleDisconnect(new SessionDisconnectEvent(
                this,
                MessageBuilder.withPayload(new byte[0]).build(),
                "session-1",
                CloseStatus.NORMAL
        ));

        verify(editLockService).releaseAll(userId);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/pictures/" + pictureId + "/collab"),
                argThat(argument -> argument instanceof CollabMessage outbound
                        && outbound.getType() == CollabMessage.EventType.PRESENCE_UPDATE
                        && pictureId.equals(outbound.getPictureId()))
        );
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/pictures/null/collab"), any());
    }

    @Test
    void shouldPersistDocumentOperationWithoutLock() throws Exception {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PictureDocumentOperationPayload payload = new PictureDocumentOperationPayload();
        payload.setId("rect-1");
        payload.setType("rect");

        CollabMessage message = CollabMessage.builder()
                .type(CollabMessage.EventType.ELEMENT_UPDATE)
                .schemaVersion(PictureCollabController.EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .payload(payload)
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
                argThat(argument -> {
                    if (!(argument instanceof CollabMessage outbound)) {
                        return false;
                    }
                    JsonNode payloadNode = outbound.getPayload();
                    return outbound.getType() == CollabMessage.EventType.ELEMENT_UPDATE
                            && pictureId.equals(outbound.getPictureId())
                            && userId.equals(outbound.getUserId())
                            && outbound.getVersion() == 7L
                            && payloadNode != null
                            && "rect-1".equals(payloadNode.path("id").asText())
                            && "rect".equals(payloadNode.path("type").asText());
                })
        );
        verify(messagingTemplate, never()).convertAndSendToUser(eq("alice"), eq("/queue/collab"), any());
    }
}
