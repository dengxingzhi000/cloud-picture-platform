package com.cn.cloudpictureplatform.application.shared.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PictureVersionResponse {
    private UUID id;
    private UUID pictureId;
    private int version;
    private long fileSize;
    private Integer width;
    private Integer height;
    private String changeNote;
    private UUID createdByUserId;
    private Instant createdAt;
}
