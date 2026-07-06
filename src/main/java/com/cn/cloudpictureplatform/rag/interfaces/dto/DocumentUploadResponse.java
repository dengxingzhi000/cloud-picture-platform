package com.cn.cloudpictureplatform.rag.interfaces.dto;

import java.util.UUID;

public record DocumentUploadResponse(UUID id, String title, String status, int chunkCount) {}
