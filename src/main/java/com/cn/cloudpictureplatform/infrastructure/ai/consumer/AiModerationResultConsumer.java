package com.cn.cloudpictureplatform.infrastructure.ai.consumer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import com.cn.cloudpictureplatform.application.picture.ModerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.annotation.RabbitListener")
public class AiModerationResultConsumer {
    private final ModerationService moderationService;
    private final ObjectMapper objectMapper;

    public AiModerationResultConsumer(ModerationService moderationService, ObjectMapper objectMapper) {
        this.moderationService = moderationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "ai.moderation.result")
    public void onModerationResult(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(message, Map.class);
            UUID pictureId = UUID.fromString((String) msg.get("pictureId"));
            boolean isSafe = Boolean.TRUE.equals(msg.get("isSafe"));
            double confidence = ((Number) msg.get("confidence")).doubleValue();
            String provider = (String) msg.get("provider");
            String modelVersion = (String) msg.get("modelVersion");
            @SuppressWarnings("unchecked")
            List<String> violations = (List<String>) msg.get("violationCategories");
            int processingMs = msg.get("processingMs") != null ? ((Number) msg.get("processingMs")).intValue() : 0;
            String rawResponse = msg.get("rawResponse") != null ? msg.get("rawResponse").toString() : null;

            moderationService.saveAiModerationResult(pictureId, provider, modelVersion,
                    isSafe, confidence, violations, rawResponse, processingMs);

            if (confidence >= 0.92 && isSafe) {
                moderationService.autoApprove(pictureId, provider);
                log.info("AI auto-approved: pictureId={}", pictureId);
            } else if (confidence >= 0.95) {
                // At this point, isSafe must be false (handled above if true)
                moderationService.autoReject(pictureId, "AI detected violations: " + violations, provider);
                log.info("AI auto-rejected: pictureId={}", pictureId);
            } else {
                log.info("AI moderation inconclusive, escalated: pictureId={}, confidence={}, isSafe={}", 
                        pictureId, confidence, isSafe);
            }
        } catch (Exception ex) {
            log.error("Failed to process moderation result", ex);
        }
    }
}
