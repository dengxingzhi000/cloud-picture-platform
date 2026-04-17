package com.cn.cloudpictureplatform.interfaces.admin.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchReindexResponse {
    private String scope;
    private int queued;
    private UUID pictureId;
    private Instant requestedAt;
}
