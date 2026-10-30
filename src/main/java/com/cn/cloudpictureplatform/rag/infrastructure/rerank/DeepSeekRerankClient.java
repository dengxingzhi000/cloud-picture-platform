package com.cn.cloudpictureplatform.rag.infrastructure.rerank;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekRerankClient implements RerankClient {

    private final WebClient deepSeekRerankWebClient;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    private static final String RERANK_PROMPT = """
        你是一个文档相关性排序专家。请判断以下文档与查询的相关性。
        
        查询：%s
        
        文档：
        %s
        
        请为每个文档给出 0.0（完全不相关）到 1.0（高度相关）的相关性评分。
        仅返回 JSON 数组，不要包含其他内容：
        [{"index": 0, "relevance_score": 0.9}, {"index": 1, "relevance_score": 0.5}]
        """;

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        var config = ragProperties.rerank();

        StringBuilder docsBuilder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            docsBuilder.append(i).append(": ").append(documents.get(i)).append("\n");
        }

        String prompt = RERANK_PROMPT.formatted(query, docsBuilder.toString());

        Map<String, Object> request = Map.of(
            "model", config.model(),
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.1,
            "max_tokens", 2048,
            "thinking", Map.of("type", "disabled")
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = deepSeekRerankWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null) {
                return fallback(documents);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return fallback(documents);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = ((String) message.get("content")).trim();

            if (content.startsWith("```")) {
                var lines = content.split("\n", 2);
                content = lines.length > 1 ? lines[1].replaceAll("```$", "").trim() : content.replaceAll("```", "").trim();
            }

            List<Map<String, Object>> results = objectMapper.readValue(
                content, new TypeReference<List<Map<String, Object>>>() {}
            );

            return results.stream()
                .sorted((a, b) -> Double.compare(
                    ((Number) b.get("relevance_score")).doubleValue(),
                    ((Number) a.get("relevance_score")).doubleValue()
                ))
                .limit(topN)
                .map(r -> new RerankResult(
                    ((Number) r.get("index")).intValue(),
                    ((Number) r.get("relevance_score")).doubleValue(),
                    documents.get(((Number) r.get("index")).intValue())
                ))
                .toList();
        } catch (Exception e) {
            log.warn("DeepSeek rerank failed, using fallback: {}", e.getMessage());
            return fallback(documents);
        }
    }

    private List<RerankResult> fallback(List<String> documents) {
        return IntStream.range(0, documents.size())
            .mapToObj(i -> new RerankResult(i, 0.0, documents.get(i)))
            .toList();
    }
}
