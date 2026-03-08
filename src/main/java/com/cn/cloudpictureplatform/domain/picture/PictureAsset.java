package com.cn.cloudpictureplatform.domain.picture;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import java.util.UUID;
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
        name = "picture_asset",
        indexes = {
                @Index(name = "idx_picture_owner", columnList = "owner_id"),
                @Index(name = "idx_picture_space", columnList = "space_id"),
                @Index(name = "idx_picture_visibility", columnList = "visibility"),
                @Index(name = "idx_picture_review", columnList = "review_status")
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

    @Column
    private Integer width;

    @Column
    private Integer height;
}
