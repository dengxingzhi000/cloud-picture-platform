package com.cn.cloudpictureplatform.domain.excalidraw;

import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "excalidraw_scene")
public class ExcalidrawScene extends BaseEntity {

    @Column(name = "picture_id", columnDefinition = "uuid")
    private UUID pictureId;

    @Column(name = "scene_name", nullable = false)
    private String sceneName;

    @Column(name = "snapshot_data", columnDefinition = "TEXT")
    private String snapshotData;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "last_seq", nullable = false)
    @Builder.Default
    private Long lastSeq = 0L;

    @Column(name = "last_updated_by_user_id", columnDefinition = "uuid")
    private UUID lastUpdatedByUserId;
}
