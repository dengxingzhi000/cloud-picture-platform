package com.cn.cloudpictureplatform.interfaces.picture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import com.cn.cloudpictureplatform.application.picture.PictureDocumentService;
import com.cn.cloudpictureplatform.application.picture.PictureCollaborationRoomService;
import com.cn.cloudpictureplatform.application.picture.PictureService;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureCollaborationRoomResponse;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureEditorDocumentResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureEditorSessionResponse;
import com.cn.cloudpictureplatform.websocket.EditLockService;
import com.cn.cloudpictureplatform.websocket.PictureCollabAccessService;
import com.cn.cloudpictureplatform.websocket.PictureCollabController;
import com.cn.cloudpictureplatform.websocket.PresenceService;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PictureControllerTests {

    @Mock
    private PictureService pictureService;

    @Mock
    private PictureDocumentService pictureDocumentService;
    @Mock
    private PictureCollaborationRoomService pictureCollaborationRoomService;

    @Mock
    private PictureCollabAccessService pictureCollabAccessService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private EditLockService editLockService;

    private PictureController controller;

    @BeforeEach
    void setUp() {
        controller = new PictureController(
                pictureService,
                pictureDocumentService,
                pictureCollaborationRoomService,
                pictureCollabAccessService,
                presenceService,
                editLockService
        );
    }

    @Test
    void shouldBuildEditorSessionContract() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUserPrincipal principal = new AppUserPrincipal(userId, "alice", "secret", true, UserRole.USER);
        PictureEditorDocumentResponse document = PictureEditorDocumentResponse.builder()
                .pictureId(pictureId)
                .schemaVersion(PictureDocumentService.DOCUMENT_SCHEMA_VERSION)
                .version(5L)
                .elements(List.of())
                .build();

        when(pictureCollabAccessService.canAccess(pictureId, userId, UserRole.USER)).thenReturn(true);
        when(pictureService.getPictureDetail(pictureId, userId, UserRole.USER))
                .thenReturn(com.cn.cloudpictureplatform.interfaces.picture.dto.PictureDetailResponse.builder()
                        .id(pictureId)
                        .canEdit(true)
                        .canManage(false)
                        .canJoinCollaboration(true)
                        .build());
        when(pictureDocumentService.getSnapshot(pictureId)).thenReturn(document);
        when(presenceService.getPresence(pictureId)).thenReturn(List.of());
        when(editLockService.getLockInfo(pictureId)).thenReturn(null);
        when(editLockService.getLockTtlSeconds()).thenReturn(300L);
        when(pictureCollaborationRoomService.buildRoom(pictureId, principal, true, false))
                .thenReturn(PictureCollaborationRoomResponse.builder()
                        .contractVersion("picture-collab-room.v1")
                        .provider("yjs-websocket")
                        .roomId("picture:" + pictureId)
                        .pictureId(pictureId)
                        .serverUrl("ws://localhost:1234")
                        .token("token")
                        .permission("EDITOR")
                        .awarenessEnabled(true)
                        .indexedDbRecommended(true)
                        .recommendedLibraries(List.of("yjs", "y-websocket", "y-indexeddb"))
                        .build());

        PictureEditorSessionResponse response = controller.getEditorSession(pictureId, principal).getData();

        assertEquals(pictureId, response.pictureId());
        assertEquals("picture-editor-session.v1", response.sessionContractVersion());
        assertEquals(document, response.document());
        assertEquals(PresenceSnapshot.builder()
                .pictureId(pictureId)
                .users(List.of())
                .lock(null)
                .build(), response.presence());
        assertEquals(PictureCollabController.EVENT_SCHEMA_VERSION, response.realtime().eventSchemaVersion());
        assertFalse(response.realtime().documentMutationRequiresLock());
        assertEquals(300L, response.realtime().lockTtlSeconds());
        assertEquals("/ws", response.realtime().websocketEndpoint());
        assertEquals("/topic/pictures/" + pictureId + "/collab", response.realtime().topicDestination());
        assertEquals("/app/pictures/" + pictureId + "/annotation", response.realtime().eventDestination());
        assertEquals("yjs-websocket", response.room().provider());
        assertEquals("picture:" + pictureId, response.room().roomId());
        assertEquals(List.of(
                "USER_JOINED",
                "USER_LEFT",
                "PRESENCE_UPDATE",
                "LOCK_ACQUIRED",
                "LOCK_RELEASED",
                "LOCK_DENIED",
                "CURSOR_UPDATE",
                "SELECTION_UPDATE",
                "ELEMENT_ADD",
                "ELEMENT_UPDATE",
                "ELEMENT_REMOVE",
                "VERSION_CONFLICT",
                "REVIEW_DECISION"
        ), response.realtime().supportedEvents());
        assertEquals(13, response.realtime().eventDefinitions().size());
    }
}
