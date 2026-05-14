package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.application.shared.dto.PictureResponse;
import org.springframework.stereotype.Component;

@Component
public class PictureResponseConverter {

    public PictureResponse toResponse(PictureAsset asset) {
        return PictureResponse.builder()
                .id(asset.getId())
                .name(asset.getName())
                .url(asset.getUrl())
                .visibility(asset.getVisibility())
                .reviewStatus(asset.getReviewStatus())
                .sizeBytes(asset.getSizeBytes())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .contentType(asset.getContentType())
                .build();
    }
}
