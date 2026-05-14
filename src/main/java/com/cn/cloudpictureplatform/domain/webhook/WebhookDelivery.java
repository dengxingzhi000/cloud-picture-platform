package com.cn.cloudpictureplatform.domain.webhook;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "webhook_delivery", indexes = {
        @Index(name = "idx_wd_webhook", columnList = "webhook_id, created_at desc")
})
public class WebhookDelivery {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "webhook_id", nullable = false, columnDefinition = "uuid")
    private UUID webhookId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "request_url", nullable = false, length = 500)
    private String requestUrl;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
