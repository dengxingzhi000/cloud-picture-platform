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
@Table(name = "ai_tool_call_audit", indexes = {
        @Index(name = "idx_atca_session", columnList = "session_id, created_at"),
        @Index(name = "idx_atca_tool", columnList = "tool_name, created_at"),
        @Index(name = "idx_atca_user", columnList = "user_id, created_at")
})
public class AiToolCallAudit {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "tool_args", columnDefinition = "text")
    private String toolArgs;

    @Column(name = "result_size")
    private Integer resultSize;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
