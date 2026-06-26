package com.cn.cloudpictureplatform.domain.picture;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "picture_asset",
        indexes = {
                @Index(name = "idx_picture_owner", columnList = "owner_id"),
                @Index(name = "idx_picture_space", columnList = "space_id"),
                @Index(name = "idx_picture_visibility", columnList = "visibility"),
                @Index(name = "idx_picture_review", columnList = "review_status"),
                @Index(name = "idx_picture_storage_key", columnList = "storage_key")
        }
)
public class PictureAsset extends BaseEntity {

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "space_id", nullable = false, columnDefinition = "uuid")
    private UUID spaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "original_filename", nullable = false, length = 200)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(length = 64)
    private String checksum;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(length = 500)
    private String url;

    @Column(name = "file_content_id", columnDefinition = "uuid")
    private UUID fileContentId;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;

    // ── Aggregate Root 业务方法 ──────────────────────────────

    public void changeVisibility(Visibility newVisibility) {
        if (newVisibility == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "visibility cannot be null");
        }
        if (this.visibility == newVisibility) {
            return;
        }
        if (newVisibility == Visibility.PUBLIC && this.reviewStatus != ReviewStatus.APPROVED
                && this.reviewStatus != ReviewStatus.AUTO_APPROVED) {
            this.reviewStatus = ReviewStatus.PENDING;
        }
        this.visibility = newVisibility;
    }

    public ReviewStatus approve() {
        if (this.visibility != Visibility.PUBLIC) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "only public pictures can be reviewed");
        }
        ReviewStatus from = this.reviewStatus;
        this.reviewStatus = ReviewStatus.APPROVED;
        return from;
    }

    public ReviewStatus reject() {
        if (this.visibility != Visibility.PUBLIC) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "only public pictures can be reviewed");
        }
        ReviewStatus from = this.reviewStatus;
        this.reviewStatus = ReviewStatus.REJECTED;
        return from;
    }

    public void autoApprove() {
        this.reviewStatus = ReviewStatus.AUTO_APPROVED;
    }

    public void autoReject() {
        this.reviewStatus = ReviewStatus.AUTO_REJECTED;
    }

    public boolean isPublic() {
        return this.visibility == Visibility.PUBLIC;
    }

    public boolean isPendingReview() {
        return this.reviewStatus == ReviewStatus.PENDING;
    }

    public boolean isApproved() {
        return this.reviewStatus == ReviewStatus.APPROVED
                || this.reviewStatus == ReviewStatus.AUTO_APPROVED;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.ownerId.equals(userId);
    }

    public void rename(String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName.trim();
        }
    }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
