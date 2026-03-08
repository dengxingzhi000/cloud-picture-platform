package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.util.UUID;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PictureSummary {

    private UUID id;
    private String name;
    private String url;
    private Visibility visibility;
    private long sizeBytes;
    private Integer width;
    private Integer height;
}
