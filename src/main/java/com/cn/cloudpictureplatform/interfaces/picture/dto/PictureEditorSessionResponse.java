package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.util.UUID;
import lombok.Builder;
import com.cn.cloudpictureplatform.websocket.dto.PresenceSnapshot;

@Builder
public record PictureEditorSessionResponse(
        UUID pictureId,
        String sessionContractVersion,
        PictureEditorDocumentResponse document,
        PresenceSnapshot presence,
        EditorRealtimeContractResponse realtime,
        PictureCollaborationRoomResponse room
) {
}
