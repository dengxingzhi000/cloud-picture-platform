package com.cn.cloudpictureplatform.interfaces.excalidraw.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateExcalidrawSceneRequest {
    private UUID pictureId;
    private String sceneName;
}
