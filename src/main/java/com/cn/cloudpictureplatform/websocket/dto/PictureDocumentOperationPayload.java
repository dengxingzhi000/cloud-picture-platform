package com.cn.cloudpictureplatform.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureDocumentOperationPayload {
    private String id;
    private String type;
    private Long baseVersion;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private String text;
}
