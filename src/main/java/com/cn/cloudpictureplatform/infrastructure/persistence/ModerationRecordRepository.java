package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import com.cn.cloudpictureplatform.domain.audit.ModerationRecord;

public interface ModerationRecordRepository extends JpaRepository<ModerationRecord, UUID>, JpaSpecificationExecutor<ModerationRecord> {
    Page<ModerationRecord> findByPictureId(UUID pictureId, Pageable pageable);

    List<ModerationRecord> findByPictureIdInOrderByReviewedAtDesc(List<UUID> pictureIds);
}
