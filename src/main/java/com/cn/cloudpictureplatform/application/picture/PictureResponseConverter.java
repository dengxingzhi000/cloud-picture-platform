package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureResponse;
import org.springframework.stereotype.Component;

/**
 * 图片响应转换器
 */
@Component
public class PictureResponseConverter {

    public PictureResponse toResponse(PictureAsset asset) {
        return new PictureResponse(
                asset.getId(),
                asset.getName(),
                asset.getUrl(),
                asset.getVisibility(),
                asset.getReviewStatus(),
                asset.getSizeBytes(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getCreatedAt().toString()
        );
    }
}
