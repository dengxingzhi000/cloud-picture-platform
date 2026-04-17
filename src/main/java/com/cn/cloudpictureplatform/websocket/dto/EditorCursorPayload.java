package com.cn.cloudpictureplatform.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditorCursorPayload {
    private Double x;
    private Double y;
}
