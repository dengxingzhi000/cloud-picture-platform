package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.watermark.WatermarkConfig;

public interface WatermarkConfigRepository extends JpaRepository<WatermarkConfig, UUID> {
    Optional<WatermarkConfig> findByTeamId(UUID teamId);
}
