package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.watermark.ExportTask;

public interface ExportTaskRepository extends JpaRepository<ExportTask, UUID> {
    Page<ExportTask> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
