package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Bootstrap contract for connecting the editor to a Yjs-compatible collaboration room.
 */
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
