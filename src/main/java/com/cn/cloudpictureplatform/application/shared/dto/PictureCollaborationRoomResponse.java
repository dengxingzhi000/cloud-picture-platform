package com.cn.cloudpictureplatform.application.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PictureCollaborationRoomResponse(
        String contractVersion,
        String provider,
        String roomId,
        UUID pictureId,
        String serverUrl,
        String token,
        Instant tokenExpiresAt,
        String permission,
        boolean awarenessEnabled,
        boolean indexedDbRecommended,
        List<String> recommendedLibraries
) {
}
