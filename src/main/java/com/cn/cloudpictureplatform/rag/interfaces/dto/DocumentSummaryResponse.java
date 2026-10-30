package com.cn.cloudpictureplatform.rag.interfaces.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        String name,
        String fileName,
        String status,
        int chunkCount,
        Instant createdAt
) {}
