package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.ai.AiCallAudit;

public interface AiCallAuditRepository extends JpaRepository<AiCallAudit, UUID> {
}
