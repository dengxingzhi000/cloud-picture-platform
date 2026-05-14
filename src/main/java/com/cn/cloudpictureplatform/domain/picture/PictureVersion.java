package com.cn.cloudpictureplatform.domain.picture;

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
@Table(name = "picture_version", indexes = {
        @Index(name = "idx_pv_picture", columnList = "picture_id"),
        @Index(name = "idx_pv_version", columnList = "picture_id, version")
})
public class PictureVersion {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "picture_id", nullable = false, columnDefinition = "uuid")
    private UUID pictureId;

    @Column(nullable = false)
    private int version;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "file_content_id", columnDefinition = "uuid")
    private UUID fileContentId;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    private Integer width;

    private Integer height;

    @Column(length = 64)
    private String checksum;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @Column(name = "created_by_user_id", nullable = false, columnDefinition = "uuid")
    private UUID createdByUserId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
