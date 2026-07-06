package com.cn.cloudpictureplatform.rag.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(@NotBlank String query) {}
