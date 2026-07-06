package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import java.util.List;

public record EmbeddingRequest(List<String> input, String model, int dimensions) {}
