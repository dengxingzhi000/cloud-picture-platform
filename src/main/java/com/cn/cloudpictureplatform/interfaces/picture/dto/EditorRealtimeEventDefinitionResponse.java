package com.cn.cloudpictureplatform.interfaces.picture.dto;

import lombok.Builder;

@Builder
public record EditorRealtimeEventDefinitionResponse(
        String eventType,
        String payloadType,
        String description
) {
}
