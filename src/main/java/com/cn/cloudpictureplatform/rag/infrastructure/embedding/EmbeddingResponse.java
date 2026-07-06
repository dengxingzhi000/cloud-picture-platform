package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import java.util.List;

public record EmbeddingResponse(List<List<Float>> embeddings, String model, int dimensions) {}
