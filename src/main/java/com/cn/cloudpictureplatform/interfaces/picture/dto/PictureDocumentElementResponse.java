package com.cn.cloudpictureplatform.interfaces.picture.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record PictureDocumentElementResponse(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("x") Double x,
        @JsonProperty("y") Double y,
        @JsonProperty("width") Double width,
        @JsonProperty("height") Double height,
        @JsonProperty("text") String text
) {
}
