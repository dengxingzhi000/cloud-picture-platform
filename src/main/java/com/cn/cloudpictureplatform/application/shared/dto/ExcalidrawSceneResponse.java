package com.cn.cloudpictureplatform.application.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ExcalidrawSceneResponse {
    private UUID id;
    private UUID pictureId;
    private String sceneName;
    private String snapshotData;
    private Long version;
    private Long lastSeq;
    private UUID lastUpdatedByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
