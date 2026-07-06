package com.cn.cloudpictureplatform.rag.infrastructure.generator;

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
public class DeepSeekGenerationClient implements GenerationClient {

    private final WebClient deepSeekWebClient;
    private final RagProperties ragProperties;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        var config = ragProperties.generation();

        Map<String, Object> request = Map.of(
            "model", config.model(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "max_tokens", config.maxTokens(),
            "temperature", config.temperature()
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = deepSeekWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
            return "抱歉，生成回答时出现错误。请查看检索到的原始文档。";
        } catch (Exception e) {
            log.error("Generation failed: {}", e.getMessage());
            return "抱歉，生成回答时出现错误。请查看检索到的原始文档。";
        }
    }
}
