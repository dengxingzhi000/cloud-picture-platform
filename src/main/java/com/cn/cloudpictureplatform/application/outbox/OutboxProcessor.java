package com.cn.cloudpictureplatform.application.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.domain.outbox.OutboxEvent;
import com.cn.cloudpictureplatform.domain.outbox.OutboxStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.OutboxEventRepository;
import com.cn.cloudpictureplatform.application.notification.NotificationPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OutboxProcessor {
    private final OutboxEventRepository outboxEventRepository;
    private final NotificationPort NotificationPort;
    private final ObjectMapper objectMapper;

    public OutboxProcessor(
            OutboxEventRepository outboxEventRepository,
            NotificationPort NotificationPort,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.NotificationPort = NotificationPort;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository.findPendingEvents(PageRequest.of(0, 50));
        for (OutboxEvent event : events) {
            try {
                processEvent(event);
                outboxEventRepository.markProcessed(event.getId(), OutboxStatus.PROCESSED, Instant.now());
            } catch (Exception ex) {
                outboxEventRepository.incrementRetry(event.getId());
                log.warn("Failed to process outbox event {} (retry {}): {}",
                        event.getId(), event.getRetryCount() + 1, ex.getMessage());
                if (event.getRetryCount() >= 5) {
                    outboxEventRepository.markProcessed(event.getId(), OutboxStatus.FAILED, Instant.now());
                    log.error("Outbox event {} permanently failed after {} retries", event.getId(), event.getRetryCount());
                }
            }
        }
    }

    private void processEvent(OutboxEvent event) {
        switch (event.getAggregateType()) {
            case "picture" -> handlePictureEvent(event);
            case "team" -> handleTeamEvent(event);
            default -> log.warn("Unknown outbox aggregate type: {}", event.getAggregateType());
        }
    }

    private void handlePictureEvent(OutboxEvent event) {
        JsonNode payload = parsePayload(event.getPayload());
        if (payload == null) return;
        UUID pictureId = event.getAggregateId();
        String pictureName = payload.path("pictureName").asText("unknown");

        switch (event.getEventType()) {
            case "UPLOAD_COMPLETE" -> {
                String ownerUsername = payload.path("ownerUsername").asText();
                NotificationPort.notifyUploadCompleted(ownerUsername, pictureId, pictureName);
            }
            case "REVIEW_DECISION" -> {
                String ownerUsername = payload.path("ownerUsername").asText();
                boolean approved = payload.path("approved").asBoolean();
                String reason = payload.path("reason").asText(null);
                NotificationPort.notifyReviewDecision(ownerUsername, pictureId, pictureName, approved, reason);
            }
            case "ADMIN_NEW_UPLOAD" -> {
                String uploaderUsername = payload.path("uploaderUsername").asText("unknown");
                NotificationPort.notifyAdminNewUpload(pictureId, pictureName, uploaderUsername);
            }
            case "TEAM_UPLOAD" -> {
                Collection<String> usernames = new ArrayList<>();
                JsonNode usernamesNode = payload.path("usernames");
                if (usernamesNode.isArray()) {
                    for (JsonNode node : usernamesNode) {
                        usernames.add(node.asText());
                    }
                }
                String uploaderUsername = payload.path("uploaderUsername").asText("unknown");
                NotificationPort.notifyTeamPictureUploaded(usernames, pictureId, pictureName, uploaderUsername);
            }
            default -> log.warn("Unknown picture event type: {}", event.getEventType());
        }
    }

    private void handleTeamEvent(OutboxEvent event) {
        JsonNode payload = parsePayload(event.getPayload());
        if (payload == null) return;
        UUID teamId = event.getAggregateId();
        String teamName = payload.path("teamName").asText("unknown");

        switch (event.getEventType()) {
            case "TEAM_INVITE" -> {
                String inviterUsername = payload.path("inviterUsername").asText("unknown");
                String inviteeUsername = payload.path("inviteeUsername").asText("unknown");
                NotificationPort.notifyTeamInvite(inviteeUsername, teamId, teamName, inviterUsername);
            }
            case "TEAM_MEMBER_JOINED" -> {
                String username = payload.path("username").asText("unknown");
                NotificationPort.notifyTeamMemberJoined(username, teamId, teamName);
            }
            default -> log.warn("Unknown team event type: {}", event.getEventType());
        }
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse outbox payload", ex);
            return null;
        }
    }
}
