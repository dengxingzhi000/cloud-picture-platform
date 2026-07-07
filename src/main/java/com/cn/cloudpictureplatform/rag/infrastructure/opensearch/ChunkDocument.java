package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import java.util.List;

public record ChunkDocument(
    String documentId,
    int chunkIndex,
    String content,
    int tokenCount,
    String title,
    String sectionPath,
    int pageNumber,
    float bm25Boost,
    int embeddingDim,
    String openSearchId,
    List<Float> embedding
) {}