package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QwenEmbeddingClient implements EmbeddingClient {

    private final WebClient qwenEmbeddingWebClient;
    private final RagProperties ragProperties;

    @Override
    public List<List<Float>> embed(List<String> texts, int dimensions) {
        var config = ragProperties.embedding();
        var request = new EmbeddingRequest(texts, config.model(), dimensions);

        EmbeddingResponse response = qwenEmbeddingWebClient.post()
            .uri("/v1/embeddings")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(EmbeddingResponse.class)
            .block();

        if (response == null || response.embeddings() == null) {
            throw new RuntimeException("Failed to get embeddings from Qwen");
        }

        log.debug("Embedded {} texts, got {} vectors of dim {}",
            texts.size(), response.embeddings().size(), dimensions);
        return response.embeddings();
    }

    @Override
    public List<Float> embedSingle(String text, int dimensions) {
        return embed(List.of(text), dimensions).get(0);
    }
}
