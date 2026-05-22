package com.cn.cloudpictureplatform.domain.excalidraw;

import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "excalidraw_file", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"scene_id", "file_id"})
})
public class ExcalidrawFile extends BaseEntity {

    @Column(name = "scene_id", nullable = false, columnDefinition = "uuid")
    private UUID sceneId;

    @Column(name = "file_id", nullable = false)
    private String fileId;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;
}
