package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.util.UUID;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PictureResponse {

    private UUID id;
    private String name;
    private String url;
    private Visibility visibility;
    private ReviewStatus reviewStatus;
    private long sizeBytes;
    private Integer width;
    private Integer height;
    private String contentType;
}
