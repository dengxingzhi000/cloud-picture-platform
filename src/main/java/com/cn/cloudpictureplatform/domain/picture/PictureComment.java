package com.cn.cloudpictureplatform.domain.picture;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "picture_comment", indexes = {
        @Index(name = "idx_pc_picture", columnList = "picture_id"),
        @Index(name = "idx_pc_parent", columnList = "parent_id")
})
public class PictureComment extends BaseEntity {

    @Column(name = "picture_id", nullable = false, columnDefinition = "uuid")
    private UUID pictureId;

    @Column(name = "author_id", nullable = false, columnDefinition = "uuid")
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Column
    private Double x;

    @Column
    private Double y;

    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;
}
