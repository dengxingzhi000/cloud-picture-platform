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
@Table(name = "ai_moderation_record", indexes = {
        @Index(name = "idx_amr_picture", columnList = "picture_id, created_at desc")
})
public class AiModerationRecord {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "picture_id", nullable = false, columnDefinition = "uuid")
    private UUID pictureId;

    @Column(length = 100)
    private String provider;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "is_safe")
    private Boolean isSafe;

    @Column
    private Double confidence;

    @Column(name = "violation_categories", columnDefinition = "text")
    private String violationCategories;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
