package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.watermark.ExportPreset;

public interface ExportPresetRepository extends JpaRepository<ExportPreset, UUID> {
    List<ExportPreset> findByTeamIdOrderByNameAsc(UUID teamId);
}
