package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.team.Team;

public interface TeamRepository extends JpaRepository<Team, UUID> {
}
