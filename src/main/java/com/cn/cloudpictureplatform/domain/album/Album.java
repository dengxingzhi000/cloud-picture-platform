package com.cn.cloudpictureplatform.domain.album;

import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "album", indexes = {
        @Index(name = "idx_album_space", columnList = "space_id"),
        @Index(name = "idx_album_visibility", columnList = "visibility")
})
public class Album extends BaseEntity {

    @Column(name = "space_id", nullable = false, columnDefinition = "uuid")
    private UUID spaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "cover_picture_id", columnDefinition = "uuid")
    private UUID coverPictureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
