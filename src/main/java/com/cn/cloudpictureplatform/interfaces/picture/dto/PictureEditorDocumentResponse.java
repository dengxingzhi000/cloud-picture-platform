package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PictureEditorDocumentResponse(
        UUID pictureId,
        String schemaVersion,
        long version,
        UUID lastUpdatedByUserId,
        Instant updatedAt,
        List<PictureDocumentElementResponse> elements
) {
}
