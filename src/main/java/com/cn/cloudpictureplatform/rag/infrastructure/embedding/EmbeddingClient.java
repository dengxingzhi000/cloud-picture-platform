package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import java.util.List;

public interface EmbeddingClient {
    List<List<Float>> embed(List<String> texts, int dimensions);
    List<Float> embedSingle(String text, int dimensions);
}
