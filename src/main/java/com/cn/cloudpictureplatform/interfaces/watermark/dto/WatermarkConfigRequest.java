package com.cn.cloudpictureplatform.interfaces.watermark.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WatermarkConfigRequest {
    private Boolean enabled;
    private String type;
    private String text;
    private String imageStorageKey;
    private Double opacity;
    private String position;
}
