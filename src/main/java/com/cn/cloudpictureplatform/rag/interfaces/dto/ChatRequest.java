package com.cn.cloudpictureplatform.rag.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank String query,
        @Size(max = 100) String sessionId
) {}
