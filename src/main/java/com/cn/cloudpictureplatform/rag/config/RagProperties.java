package com.cn.cloudpictureplatform.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
    boolean enabled,
    OpenSearch openSearch,
    Embedding embedding,
    Rerank rerank,
    Generation generation,
    Chunking chunking,
    Retrieval retrieval,
    Cache cache
) {
    public record OpenSearch(
        String baseUrl,
        String indexName,
        int connectTimeoutMs,
        int socketTimeoutMs
    ) {}

    public record Embedding(
        String baseUrl,
        String model,
        int dimensions,
        int batchSize,
        Duration timeout
    ) {}

    public record Rerank(
        String baseUrl,
        String model,
        int topN,
        boolean enabled,
        Duration timeout
    ) {}

    public record Generation(
        String model,
        int maxTokens,
        double temperature
    ) {}

    public record Chunking(
        int maxTokens,
        int overlapTokens,
        int minChunkTokens
    ) {}

    public record Retrieval(
        int bm25TopK,
        int vectorTopK,
        int rrfK,
        int finalTopN
    ) {}

    public record Cache(
        Duration embeddingTtl,
        Duration retrievalTtl,
        boolean semanticEnabled,
        double semanticThreshold
    ) {}
}
