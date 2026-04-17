package com.cn.cloudpictureplatform.websocket.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditorSelectionPayload {
    private String activeElementId;
    private List<String> elementIds;
}
