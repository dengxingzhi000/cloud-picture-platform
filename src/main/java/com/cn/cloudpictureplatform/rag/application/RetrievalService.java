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
        return retrieve(query, retrievalConfig.bm25TopK(), null);
    }

    public List<RetrievalResult> retrieve(String query, int topN) {
        return retrieve(query, topN, null);
    }

    public List<RetrievalResult> retrieve(String query, int topN, List<Float> precomputedEmbedding) {
        var embedConfig = ragProperties.embedding();

        List<Float> queryEmbedding = precomputedEmbedding != null
            ? precomputedEmbedding
            : embeddingClient.embedSingle(query, embedConfig.dimensions());

        List<RetrievalResult> results = hybridSearchClient.hybridSearch(query, queryEmbedding, topN);

        log.debug("Retrieved {} results for query: {}", results.size(), query);
        return results;
    }
}
