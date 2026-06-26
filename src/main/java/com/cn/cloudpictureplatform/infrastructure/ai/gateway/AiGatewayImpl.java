package com.cn.cloudpictureplatform.infrastructure.ai.gateway;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.cn.cloudpictureplatform.domain.ai.AiChatRequest;
import com.cn.cloudpictureplatform.domain.ai.AiChatResponse;
import com.cn.cloudpictureplatform.domain.ai.AiGateway;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiCallAuditRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiGatewayImpl implements AiGateway {
    private final RestClient restClient;
    private final AiCallAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final int chatTimeout;

    public AiGatewayImpl(
            AiProperties properties,
            AiCallAuditRepository auditRepository,
            ObjectMapper objectMapper
    ) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.chatTimeout = properties.getGateway().getTimeoutMs().getChat();

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(chatTimeout));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getGateway().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    @CircuitBreaker(name = "ai-embedding", fallbackMethod = "embedTextFallback")
    public Optional<float[]> embedText(String text) {
        long start = System.currentTimeMillis();
        try {
            String body = restClient.post()
                    .uri("/api/v1/embedding/text")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(text, "text"))
                    .retrieve()
                    .body(String.class);
            audit("embedding", true, System.currentTimeMillis() - start);
            return Optional.of(parseVector(objectMapper.readTree(body)));
        } catch (Exception ex) {
            audit("embedding", false, System.currentTimeMillis() - start);
            log.warn("AI embedding failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<float[]> embedImage(String imageUrl) {
        long start = System.currentTimeMillis();
        try {
            String body = restClient.post()
                    .uri("/api/v1/embedding/image")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(imageUrl, "image"))
                    .retrieve()
                    .body(String.class);
            audit("embedding", true, System.currentTimeMillis() - start);
            return Optional.of(parseVector(objectMapper.readTree(body)));
        } catch (Exception ex) {
            audit("embedding", false, System.currentTimeMillis() - start);
            log.warn("AI image embedding failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void submitTaggingTask(UUID pictureId, String imageUrl) {
        try {
            restClient.post()
                    .uri("/api/v1/tagging/submit")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new TaskSubmit("IMAGE_TAGGING", pictureId, imageUrl))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Tagging task submitted: pictureId={}", pictureId);
        } catch (Exception ex) {
            log.warn("Failed to submit tagging task: {}", ex.getMessage());
        }
    }

    @Override
    public void submitModerationTask(UUID pictureId, String imageUrl) {
        try {
            restClient.post()
                    .uri("/api/v1/moderation/submit")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new TaskSubmit("MODERATION", pictureId, imageUrl))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Moderation task submitted: pictureId={}", pictureId);
        } catch (Exception ex) {
            log.warn("Failed to submit moderation task: {}", ex.getMessage());
        }
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        long start = System.currentTimeMillis();
        try {
            String contextIds = request.contextPictureIds() != null
                    ? request.contextPictureIds().stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(null)
                    : null;
            ChatPayload payload = new ChatPayload(
                    request.sessionId(),
                    request.message(),
                    contextIds != null ? java.util.List.of(contextIds.split(",")) : null
            );
            String respBody = restClient.post()
                    .uri("/api/v1/assistant/chat")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(respBody);
            log.info("AI chat response: {}", json);

            var data = json != null ? json.get("data") : null;
            if (data == null || data.isNull()) {
                auditChat("chat", true, System.currentTimeMillis() - start,
                        truncate(request.message(), 200), null, null);
                return new AiChatResponse(request.sessionId(), "AI service unavailable", "error", java.util.List.of());
            }

            Integer tokensUsed = json.get("tokensUsed") != null && !json.get("tokensUsed").isNull()
                    ? json.get("tokensUsed").asInt() : null;
            Integer toolCallsCount = json.get("toolCallsCount") != null && !json.get("toolCallsCount").isNull()
                    ? json.get("toolCallsCount").asInt() : null;

            auditChat("chat", true, System.currentTimeMillis() - start,
                    truncate(request.message(), 200), tokensUsed, toolCallsCount);

            return new AiChatResponse(
                    data.get("sessionId").asText(),
                    data.get("reply").asText(),
                    data.get("intent").asText(),
                    objectMapper.convertValue(data.get("suggestedActions"),
                            objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class))
            );
        } catch (Exception ex) {
            auditChat("chat", false, System.currentTimeMillis() - start,
                    truncate(request.message(), 200), null, null);
            log.warn("AI chat failed: {}", ex.getMessage());
            return new AiChatResponse(request.sessionId(), "AI service unavailable", "error", java.util.List.of());
        }
    }

    @SuppressWarnings("unused")
    private Optional<float[]> embedTextFallback(String text, Throwable t) {
        log.warn("AI embedding circuit breaker fallback: {}", t.getMessage());
        return Optional.empty();
    }

    private float[] parseVector(JsonNode json) {
        var arr = json != null ? json.get("vector") : null;
        if (arr == null || arr.isNull()) return new float[0];
        float[] vector = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) vector[i] = (float) arr.get(i).asDouble();
        return vector;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private void audit(String taskType, boolean success, long latencyMs) {
        try {
            auditRepository.save(com.cn.cloudpictureplatform.domain.ai.AiCallAudit.builder()
                    .taskType(taskType).success(success).latencyMs((int) latencyMs)
                    .createdAt(java.time.Instant.now()).build());
        } catch (Exception ex) {
            log.warn("Failed to save AI audit: {}", ex.getMessage());
        }
    }

    private void auditChat(String taskType, boolean success, long latencyMs, String requestSummary,
                            Integer tokensUsed, Integer toolCallsCount) {
        try {
            auditRepository.save(com.cn.cloudpictureplatform.domain.ai.AiCallAudit.builder()
                    .taskType(taskType).success(success).latencyMs((int) latencyMs)
                    .requestSummary(requestSummary)
                    .tokensUsed(tokensUsed)
                    .toolCallsCount(toolCallsCount)
                    .createdAt(java.time.Instant.now()).build());
        } catch (Exception ex) {
            log.warn("Failed to save AI audit: {}", ex.getMessage());
        }
    }

    private record EmbeddingRequest(String content, String inputType) {}
    private record TaskSubmit(String taskType, UUID pictureId, String imageUrl) {}
    private record ChatPayload(String sessionId, String message, java.util.List<String> contextPictureIds) {}
}
