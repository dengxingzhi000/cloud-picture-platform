package com.cn.cloudpictureplatform.domain.album;

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
@Table(name = "album_picture", indexes = {
        @Index(name = "idx_album_picture_album", columnList = "album_id"),
        @Index(name = "idx_album_picture_picture", columnList = "picture_id")
})
public class AlbumPicture {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "album_id", nullable = false, columnDefinition = "uuid")
    private UUID albumId;

    @Column(name = "picture_id", nullable = false, columnDefinition = "uuid")
    private UUID pictureId;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
