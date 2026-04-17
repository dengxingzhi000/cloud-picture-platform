package com.cn.cloudpictureplatform.interfaces.picture;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import com.cn.cloudpictureplatform.application.picture.PictureService;
import com.cn.cloudpictureplatform.application.picture.PictureDocumentService;
import com.cn.cloudpictureplatform.application.picture.PictureCollaborationRoomService;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.EditorRealtimeEventDefinitionResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureTagCreateRequest;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureDetailResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureEditorDocumentResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureEditorSessionResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.EditorRealtimeContractResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureCollaborationRoomResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureTagResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureSummary;
import com.cn.cloudpictureplatform.websocket.EditLockService;
import com.cn.cloudpictureplatform.websocket.PictureCollabAccessService;
import com.cn.cloudpictureplatform.websocket.PictureCollabController;
import com.cn.cloudpictureplatform.websocket.PresenceService;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;

@Validated
@RestController
@RequestMapping("/api/pictures")
public class PictureController {
    private static final String SESSION_CONTRACT_VERSION = "picture-editor-session.v1";

    private final PictureService pictureService;
    private final PictureDocumentService pictureDocumentService;
    private final PictureCollaborationRoomService pictureCollaborationRoomService;
    private final PictureCollabAccessService pictureCollabAccessService;
    private final PresenceService presenceService;
    private final EditLockService editLockService;

    public PictureController(
            PictureService pictureService,
            PictureDocumentService pictureDocumentService,
            PictureCollaborationRoomService pictureCollaborationRoomService,
            PictureCollabAccessService pictureCollabAccessService,
            PresenceService presenceService,
            EditLockService editLockService
    ) {
        this.pictureService = pictureService;
        this.pictureDocumentService = pictureDocumentService;
        this.pictureCollaborationRoomService = pictureCollaborationRoomService;
        this.pictureCollabAccessService = pictureCollabAccessService;
        this.presenceService = presenceService;
        this.editLockService = editLockService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PictureResponse> upload(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "spaceId", required = false) UUID spaceId
    ) {
        return ApiResponse.ok(pictureService.upload(principal.getId(), file, visibility, name, spaceId));
    }

    @GetMapping("/public")
    public ApiResponse<PageResponse<PictureSummary>> listPublic(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long minSizeBytes,
            @RequestParam(required = false) Long maxSizeBytes,
            @RequestParam(required = false) String orientation
    ) {
        return ApiResponse.ok(pictureService.listPublic(
                page,
                size,
                keyword,
                minSizeBytes,
                maxSizeBytes,
                orientation
        ));
    }

    @GetMapping("/recommendations")
    public ApiResponse<PageResponse<PictureSummary>> listRecommendations(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(pictureService.recommendPublic(
                page,
                size,
                principal == null ? null : principal.getId()
        ));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<PictureSummary>> search(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) Long minSizeBytes,
            @RequestParam(required = false) Long maxSizeBytes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @RequestParam(required = false) String orientation,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ApiResponse.ok(pictureService.searchPictures(
                page,
                size,
                keyword,
                ownerId,
                spaceId,
                visibility,
                reviewStatus,
                minSizeBytes,
                maxSizeBytes,
                createdAfter,
                createdBefore,
                orientation,
                tag,
                tagId,
                sortBy,
                sortDir,
                principal == null ? null : principal.getId(),
                principal == null ? null : principal.getRole()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<PictureDetailResponse> getDetail(
            @PathVariable("id") UUID pictureId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(pictureService.getPictureDetail(
                pictureId,
                principal == null ? null : principal.getId(),
                principal == null ? null : principal.getRole()
        ));
    }

    @GetMapping("/{id}/document")
    public ApiResponse<PictureEditorDocumentResponse> getDocument(
            @PathVariable("id") UUID pictureId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        verifyEditorAccess(pictureId, principal);
        return ApiResponse.ok(pictureDocumentService.getSnapshot(pictureId));
    }

    @GetMapping("/{id}/editor-session")
    public ApiResponse<PictureEditorSessionResponse> getEditorSession(
            @PathVariable("id") UUID pictureId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        verifyEditorAccess(pictureId, principal);
        PictureDetailResponse detail = getEditorDetail(pictureId, principal);
        PictureEditorDocumentResponse document = pictureDocumentService.getSnapshot(pictureId);
        PresenceSnapshot presence = PresenceSnapshot.builder()
                .pictureId(pictureId)
                .users(presenceService.getPresence(pictureId))
                .lock(editLockService.getLockInfo(pictureId))
                .build();
        return ApiResponse.ok(PictureEditorSessionResponse.builder()
                .pictureId(pictureId)
                .sessionContractVersion(SESSION_CONTRACT_VERSION)
                .document(document)
                .presence(presence)
                        .realtime(EditorRealtimeContractResponse.builder()
                        .eventSchemaVersion(PictureCollabController.EVENT_SCHEMA_VERSION)
                        .documentMutationRequiresLock(false)
                        .documentVersionCheckSupported(true)
                        .lockTtlSeconds(editLockService.getLockTtlSeconds())
                        .websocketEndpoint("/ws")
                        .topicDestination("/topic/pictures/" + pictureId + "/collab")
                        .userQueueDestination("/user/queue/collab")
                        .joinDestination("/app/pictures/" + pictureId + "/join")
                        .leaveDestination("/app/pictures/" + pictureId + "/leave")
                        .lockDestination("/app/pictures/" + pictureId + "/lock")
                        .lockRefreshDestination("/app/pictures/" + pictureId + "/lock/refresh")
                        .unlockDestination("/app/pictures/" + pictureId + "/unlock")
                        .eventDestination("/app/pictures/" + pictureId + "/annotation")
                        .supportedEvents(List.of(
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
                        ))
                        .eventDefinitions(List.of(
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("USER_JOINED")
                                        .payloadType("none")
                                        .description("Broadcast when a collaborator joins the editor session.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("USER_LEFT")
                                        .payloadType("none")
                                        .description("Broadcast when a collaborator leaves the editor session.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("PRESENCE_UPDATE")
                                        .payloadType("PresenceSnapshot")
                                        .description("Broadcast full collaborator and lock snapshot after presence changes.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("LOCK_ACQUIRED")
                                        .payloadType("LockInfo")
                                        .description("Broadcast when a user acquires or refreshes the editor lock.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("LOCK_RELEASED")
                                        .payloadType("none")
                                        .description("Broadcast when the active editor lock is released.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("LOCK_DENIED")
                                        .payloadType("LockInfo")
                                        .description("Sent on the user queue when another user already holds the optional editor lock.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("CURSOR_UPDATE")
                                        .payloadType("EditorCursorPayload")
                                        .description("Broadcast collaborator cursor coordinates inside the canvas.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("SELECTION_UPDATE")
                                        .payloadType("EditorSelectionPayload")
                                        .description("Broadcast collaborator selection state for active elements.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("ELEMENT_ADD")
                                        .payloadType("PictureDocumentElementResponse")
                                        .description("Broadcast a newly persisted editor element. Clients should send payload.baseVersion from the latest document snapshot.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("ELEMENT_UPDATE")
                                        .payloadType("PictureDocumentElementResponse")
                                        .description("Broadcast a persisted editor element update with incremented version. Clients should send payload.baseVersion from the latest document snapshot.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("ELEMENT_REMOVE")
                                        .payloadType("PictureDocumentElementResponse")
                                        .description("Broadcast the removed editor element and resulting version. Clients should send payload.baseVersion from the latest document snapshot.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("VERSION_CONFLICT")
                                        .payloadType("PictureEditorDocumentResponse")
                                        .description("Sent on the user queue with the latest document snapshot when baseVersion is stale or a concurrent write wins.")
                                        .build(),
                                EditorRealtimeEventDefinitionResponse.builder()
                                        .eventType("REVIEW_DECISION")
                                        .payloadType("NotificationMessage")
                                        .description("Broadcast on the collaboration topic when moderation changes the current picture status.")
                                        .build()
                        ))
                        .build())
                .room(buildCollaborationRoom(pictureId, principal, detail))
                .build());
    }

    @GetMapping("/{id}/collaboration-room")
    public ApiResponse<PictureCollaborationRoomResponse> getCollaborationRoom(
            @PathVariable("id") UUID pictureId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        verifyEditorAccess(pictureId, principal);
        PictureDetailResponse detail = getEditorDetail(pictureId, principal);
        return ApiResponse.ok(buildCollaborationRoom(pictureId, principal, detail));
    }

    @PostMapping("/{id}/collaboration-room/refresh")
    public ApiResponse<PictureCollaborationRoomResponse> refreshCollaborationRoom(
            @PathVariable("id") UUID pictureId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        verifyEditorAccess(pictureId, principal);
        PictureDetailResponse detail = getEditorDetail(pictureId, principal);
        return ApiResponse.ok(pictureCollaborationRoomService.refreshRoomToken(
                pictureId,
                principal,
                detail.isCanEdit(),
                detail.isCanManage()
        ));
    }

    @GetMapping("/{id}/tags")
    public ApiResponse<List<PictureTagResponse>> listTags(@PathVariable("id") UUID pictureId) {
        return ApiResponse.ok(pictureService.listTags(pictureId));
    }

    @PostMapping("/{id}/tags")
    public ApiResponse<List<PictureTagResponse>> addTags(
            @PathVariable("id") UUID pictureId,
            @Valid @RequestBody PictureTagCreateRequest request
    ) {
        return ApiResponse.ok(pictureService.addTags(pictureId, request));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ApiResponse<Void> removeTag(
            @PathVariable("id") UUID pictureId,
            @PathVariable("tagId") UUID tagId
    ) {
        pictureService.removeTag(pictureId, tagId);
        return ApiResponse.ok(null);
    }

    private void verifyEditorAccess(UUID pictureId, AppUserPrincipal principal) {
        if (principal == null
                || !pictureCollabAccessService.canAccess(pictureId, principal.getId(), principal.getRole())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "insufficient permissions");
        }
    }

    private PictureDetailResponse getEditorDetail(UUID pictureId, AppUserPrincipal principal) {
        return pictureService.getPictureDetail(
                pictureId,
                principal.getId(),
                principal.getRole()
        );
    }

    private PictureCollaborationRoomResponse buildCollaborationRoom(
            UUID pictureId,
            AppUserPrincipal principal,
            PictureDetailResponse detail
    ) {
        return pictureCollaborationRoomService.buildRoom(
                pictureId,
                principal,
                detail.isCanEdit(),
                detail.isCanManage()
        );
    }
}
