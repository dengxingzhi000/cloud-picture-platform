package com.cn.cloudpictureplatform.domain.watermark;

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
@Table(name = "export_preset", indexes = {
        @Index(name = "idx_ep_team", columnList = "team_id")
})
public class ExportPreset extends BaseEntity {

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_width", nullable = false)
    private int targetWidth;

    @Column(name = "target_height", nullable = false)
    private int targetHeight;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String format = "JPEG";

    @Column(nullable = false)
    @Builder.Default
    private int quality = 90;

    @Column(name = "watermark_enabled", nullable = false)
    @Builder.Default
    private boolean watermarkEnabled = true;
}
