package com.cn.cloudpictureplatform.rag.infrastructure.rerank;

public record RerankResult(int index, double score, String document) {}
