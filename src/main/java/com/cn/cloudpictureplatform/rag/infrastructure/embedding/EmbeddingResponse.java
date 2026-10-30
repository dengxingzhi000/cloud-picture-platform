package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import java.util.List;

public record EmbeddingResponse(List<EmbeddingData> data, String model) {

    public record EmbeddingData(List<Float> embedding, int index) {}
}
