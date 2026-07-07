package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

public record TextChunk(
    int chunkIndex,
    String content,
    int tokenCount,
    int startOffset,
    int endOffset
) {}