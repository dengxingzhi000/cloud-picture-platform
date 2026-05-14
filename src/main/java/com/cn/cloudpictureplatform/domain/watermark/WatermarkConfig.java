package com.cn.cloudpictureplatform.domain.watermark;

import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "watermark_config")
public class WatermarkConfig extends BaseEntity {

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid", unique = true)
    private UUID teamId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String type = "TEXT";

    @Column(length = 200)
    private String text;

    @Column(name = "image_storage_key", length = 500)
    private String imageStorageKey;

    @Column(nullable = false)
    @Builder.Default
    private double opacity = 0.5;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String position = "BOTTOM_RIGHT";
}
