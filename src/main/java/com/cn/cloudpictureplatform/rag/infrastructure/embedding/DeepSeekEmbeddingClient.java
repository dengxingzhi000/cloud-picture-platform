package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

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
public class DeepSeekEmbeddingClient implements EmbeddingClient {

    private final WebClient deepSeekEmbeddingWebClient;
    private final RagProperties ragProperties;

    @Override
    public List<List<Float>> embed(List<String> texts, int dimensions) {
        var config = ragProperties.embedding();
        var text = String.join("\n", texts);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = deepSeekEmbeddingWebClient.post()
            .uri("/api/v1/embedding/text")
            .bodyValue(Map.of("text", text, "model", config.model()))
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response == null) {
            throw new RuntimeException("Failed to get embeddings from AI platform");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            throw new RuntimeException("Empty embedding response from AI platform");
        }

        @SuppressWarnings("unchecked")
        List<Double> vector = (List<Double>) data.get("vector");
        if (vector == null) {
            throw new RuntimeException("No vector in embedding response");
        }

        var result = vector.stream().map(Double::floatValue).toList();

        log.debug("Embedded {} texts, got vector of dim {}", texts.size(), result.size());
        return texts.stream().map(t -> result).toList();
    }

    @Override
    public List<Float> embedSingle(String text, int dimensions) {
        return embed(List.of(text), dimensions).get(0);
    }
}
