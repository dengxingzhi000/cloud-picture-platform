package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.team.TeamActivity;

public interface TeamActivityRepository extends JpaRepository<TeamActivity, UUID> {
    Page<TeamActivity> findByTeamIdOrderByCreatedAtDesc(UUID teamId, Pageable pageable);
}
