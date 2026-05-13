package com.cn.cloudpictureplatform.application.picture;

import java.util.UUID;

public record PictureUploadedEvent(
        UUID pictureId,
        String pictureName,
        UUID ownerId,
        UUID spaceId,
        java.util.UUID teamId,
        boolean isPublic
) {
}
