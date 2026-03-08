package com.cn.cloudpictureplatform.domain.audit;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "moderation_record",
        indexes = {
                @Index(name = "idx_moderation_picture", columnList = "picture_id"),
                @Index(name = "idx_moderation_reviewer", columnList = "reviewer_id")
        }
)
public class ModerationRecord extends BaseEntity {

    @Column(name = "picture_id", nullable = false, columnDefinition = "uuid")
    private UUID pictureId;

    @Column(name = "reviewer_id", nullable = false, columnDefinition = "uuid")
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private ReviewStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ReviewStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;
}
