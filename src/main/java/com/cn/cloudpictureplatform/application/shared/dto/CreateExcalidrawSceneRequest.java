package com.cn.cloudpictureplatform.application.shared.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateExcalidrawSceneRequest {
    private UUID pictureId;
    private String sceneName;
}
