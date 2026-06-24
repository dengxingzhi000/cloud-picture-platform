package com.cn.cloudpictureplatform.infrastructure.ai.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.domain.ai.AiChatRequest;
import com.cn.cloudpictureplatform.domain.ai.AiChatResponse;
import com.cn.cloudpictureplatform.domain.ai.AiGateway;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiCallAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiGatewayImpl implements AiGateway {
    private final HttpClient httpClient;
    private final AiCallAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int embeddingTimeout;
    private final int chatTimeout;

    public AiGatewayImpl(
            AiProperties properties,
            AiCallAuditRepository auditRepository,
            ObjectMapper objectMapper
    ) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.baseUrl = properties.getGateway().getBaseUrl();
        this.embeddingTimeout = properties.getGateway().getTimeoutMs().getEmbedding();
        this.chatTimeout = properties.getGateway().getTimeoutMs().getChat();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    @CircuitBreaker(name = "ai-embedding", fallbackMethod = "embedTextFallback")
    public Optional<float[]> embedText(String text) {
        long start = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(new EmbeddingRequest(text, "text"));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/embedding/text"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(embeddingTimeout))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            audit("embedding", true, System.currentTimeMillis() - start);
            var json = objectMapper.readTree(resp.body());
            var arr = json.get("vector");
            float[] vector = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) vector[i] = (float) arr.get(i).asDouble();
            return Optional.of(vector);
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
            String body = objectMapper.writeValueAsString(new EmbeddingRequest(imageUrl, "image"));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/embedding/image"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(embeddingTimeout * 2L))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            audit("embedding", true, System.currentTimeMillis() - start);
            var json = objectMapper.readTree(resp.body());
            var arr = json.get("vector");
            float[] vector = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) vector[i] = (float) arr.get(i).asDouble();
            return Optional.of(vector);
        } catch (Exception ex) {
            audit("embedding", false, System.currentTimeMillis() - start);
            log.warn("AI image embedding failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void submitTaggingTask(UUID pictureId, String imageUrl) {
        try {
            String body = objectMapper.writeValueAsString(new TaskSubmit("IMAGE_TAGGING", pictureId, imageUrl));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/tagging/submit"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
            log.info("Tagging task submitted: pictureId={}", pictureId);
        } catch (Exception ex) {
            log.warn("Failed to submit tagging task: {}", ex.getMessage());
        }
    }

    @Override
    public void submitModerationTask(UUID pictureId, String imageUrl) {
        try {
            String body = objectMapper.writeValueAsString(new TaskSubmit("MODERATION", pictureId, imageUrl));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/moderation/submit"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
            log.info("Moderation task submitted: pictureId={}", pictureId);
        } catch (Exception ex) {
            log.warn("Failed to submit moderation task: {}", ex.getMessage());
        }
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        long start = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/assistant/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(chatTimeout))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            audit("chat", true, System.currentTimeMillis() - start);
            return objectMapper.readValue(resp.body(), AiChatResponse.class);
        } catch (Exception ex) {
            audit("chat", false, System.currentTimeMillis() - start);
            log.warn("AI chat failed: {}", ex.getMessage());
            return new AiChatResponse(request.sessionId(), "AI service unavailable", "error", java.util.List.of());
        }
    }

    @SuppressWarnings("unused")
    private Optional<float[]> embedTextFallback(String text, Throwable t) {
        log.warn("AI embedding circuit breaker fallback: {}", t.getMessage());
        return Optional.empty();
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

    private record EmbeddingRequest(String content, String inputType) {}
    private record TaskSubmit(String taskType, UUID pictureId, String imageUrl) {}
}
