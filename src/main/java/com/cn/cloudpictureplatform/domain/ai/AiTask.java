package com.cn.cloudpictureplatform.domain.ai;

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
@Table(name = "ai_task", indexes = {
        @Index(name = "idx_at_status", columnList = "status, submitted_at")
})
public class AiTask {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(name = "picture_id", columnDefinition = "uuid")
    private UUID pictureId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @CreatedDate
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "result_summary", columnDefinition = "text")
    private String resultSummary;
}
