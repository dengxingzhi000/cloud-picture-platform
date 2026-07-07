package com.cn.cloudpictureplatform.rag.infrastructure.rerank;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QwenRerankClient implements RerankClient {

    private final WebClient qwenRerankWebClient;
    private final RagProperties ragProperties;

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        var config = ragProperties.rerank();

        Map<String, Object> request = Map.of(
            "model", config.model(),
            "query", query,
            "documents", documents,
            "top_n", topN,
            "return_documents", false
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = qwenRerankWebClient.post()
                .uri("/v1/rerank")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

            return results.stream()
                .map(r -> new RerankResult(
                    ((Number) r.get("index")).intValue(),
                    ((Number) r.get("relevance_score")).doubleValue(),
                    documents.get(((Number) r.get("index")).intValue())
                ))
                .toList();
        } catch (Exception e) {
            log.warn("Rerank fallback: returning candidates with sentinel score (0.0): {}", e.getMessage());
            return java.util.stream.IntStream.range(0, documents.size())
                .mapToObj(i -> new RerankResult(i, 0.0, documents.get(i)))
                .toList();
        }
    }
}
