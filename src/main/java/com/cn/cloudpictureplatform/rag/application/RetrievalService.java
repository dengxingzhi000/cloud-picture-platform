package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import com.cn.cloudpictureplatform.rag.infrastructure.embedding.EmbeddingClient;
import com.cn.cloudpictureplatform.rag.infrastructure.opensearch.HybridSearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final HybridSearchClient hybridSearchClient;
    private final EmbeddingClient embeddingClient;
    private final RagProperties ragProperties;

    public List<RetrievalResult> retrieve(String query) {
        var retrievalConfig = ragProperties.retrieval();
        var embedConfig = ragProperties.embedding();

        List<Float> queryEmbedding = embeddingClient.embedSingle(query, embedConfig.dimensions());

        List<RetrievalResult> results = hybridSearchClient.hybridSearch(
            query, queryEmbedding, retrievalConfig.bm25TopK()
        );

        log.debug("Retrieved {} results for query: {}", results.size(), query);
        return results;
    }

    public List<RetrievalResult> retrieve(String query, int topN) {
        var embedConfig = ragProperties.embedding();
        List<Float> queryEmbedding = embeddingClient.embedSingle(query, embedConfig.dimensions());
        return hybridSearchClient.hybridSearch(query, queryEmbedding, topN);
    }
}
