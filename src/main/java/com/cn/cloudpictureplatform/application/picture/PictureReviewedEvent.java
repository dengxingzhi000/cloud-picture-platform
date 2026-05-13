package com.cn.cloudpictureplatform.application.picture;

import java.util.UUID;

public record PictureReviewedEvent(
        UUID pictureId,
        String pictureName,
        UUID ownerId,
        boolean approved,
        String reason
) {
}
