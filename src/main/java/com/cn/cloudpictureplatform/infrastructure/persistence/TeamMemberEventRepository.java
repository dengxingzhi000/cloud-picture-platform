package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.cn.cloudpictureplatform.domain.team.TeamMemberEvent;

public interface TeamMemberEventRepository
        extends JpaRepository<TeamMemberEvent, UUID>, JpaSpecificationExecutor<TeamMemberEvent> {
}
