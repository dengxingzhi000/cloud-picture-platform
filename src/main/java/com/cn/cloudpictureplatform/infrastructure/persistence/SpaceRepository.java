package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.space.Space;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    Optional<Space> findByOwnerId(UUID ownerId);

    Optional<Space> findByTeamId(UUID teamId);

    List<Space> findByTeamIdIn(List<UUID> teamIds);
}
