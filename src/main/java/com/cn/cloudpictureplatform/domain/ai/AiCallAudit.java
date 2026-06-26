package com.cn.cloudpictureplatform.domain.ai;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "ai_call_audit", indexes = {
        @Index(name = "idx_aca_type", columnList = "task_type, created_at")
})
public class AiCallAudit {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(name = "picture_id", columnDefinition = "uuid")
    private UUID pictureId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(length = 100)
    private String provider;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "cost_units")
    private Integer costUnits;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "tool_calls_count")
    private Integer toolCallsCount;

    @Column(name = "request_summary", length = 500)
    private String requestSummary;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
