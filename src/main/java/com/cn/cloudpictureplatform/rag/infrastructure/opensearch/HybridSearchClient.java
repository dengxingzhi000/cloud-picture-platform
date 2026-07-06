package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.KnnQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Component;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class HybridSearchClient {

    private final OpenSearchClient openSearchClient;
    private final RagProperties ragProperties;

    public List<RetrievalResult> hybridSearch(String query, List<Float> queryEmbedding, int topK) {
        try {
            var config = ragProperties.openSearch();

            SearchRequest bm25Request = SearchRequest.of(s -> s
                .index(config.indexName())
                .query(q -> q
                    .multiMatch(m -> m
                        .query(query)
                        .fields("content^1.0", "title^2.0", "sectionPath^1.5", "documentId^3.0")
                    )
                )
                .size(topK)
            );
            SearchResponse<ChunkDocument> bm25Response = openSearchClient.search(bm25Request, ChunkDocument.class);

            float[] vector = new float[queryEmbedding.size()];
            for (int i = 0; i < queryEmbedding.size(); i++) {
                vector[i] = queryEmbedding.get(i);
            }

            SearchRequest vectorRequest = SearchRequest.of(s -> s
                .index(config.indexName())
                .query(q -> q
                    .knn(k -> k
                        .field("embedding")
                        .k(topK)
                        .vector(vector)
                    )
                )
                .size(topK)
            );
            SearchResponse<ChunkDocument> vectorResponse = openSearchClient.search(vectorRequest, ChunkDocument.class);

            return mergeWithRRF(bm25Response, vectorResponse, topK);

        } catch (Exception e) {
            throw new RuntimeException("Hybrid search failed", e);
        }
    }

    private List<RetrievalResult> mergeWithRRF(
            SearchResponse<ChunkDocument> bm25Response,
            SearchResponse<ChunkDocument> vectorResponse,
            int topK) {

        int k = ragProperties.retrieval().rrfK();
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, ChunkDocument> chunkMap = new HashMap<>();

        // Standard RRF: 1/(k + rank) where rank is 1-based position
        List<Hit<ChunkDocument>> bm25Hits = bm25Response.hits().hits();
        for (int rank = 0; rank < bm25Hits.size(); rank++) {
            ChunkDocument doc = bm25Hits.get(rank).source();
            if (doc != null) {
                String id = doc.openSearchId();
                rrfScores.merge(id, 1.0 / (k + rank + 1), Double::sum);
                chunkMap.put(id, doc);
            }
        }

        List<Hit<ChunkDocument>> vectorHits = vectorResponse.hits().hits();
        for (int rank = 0; rank < vectorHits.size(); rank++) {
            ChunkDocument doc = vectorHits.get(rank).source();
            if (doc != null) {
                String id = doc.openSearchId();
                rrfScores.merge(id, 1.0 / (k + rank + 1), Double::sum);
                chunkMap.put(id, doc);
            }
        }

        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> {
                ChunkDocument doc = chunkMap.get(e.getKey());
                return new RetrievalResult(
                    doc.openSearchId(),
                    doc.documentId(),
                    doc.content(),
                    doc.title(),
                    doc.sectionPath(),
                    doc.pageNumber(),
                    e.getValue(),
                    0.0,
                    0.0,
                    List.of(doc.title())
                );
            })
            .toList();
    }
}
