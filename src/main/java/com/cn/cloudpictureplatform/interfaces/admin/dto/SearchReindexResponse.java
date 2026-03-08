package com.cn.cloudpictureplatform.interfaces.admin.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchReindexResponse {
    private int queued;
    private Instant requestedAt;
}
