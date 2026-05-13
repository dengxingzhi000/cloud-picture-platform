package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.space.Space;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    Optional<Space> findFirstByOwnerIdAndType(UUID ownerId, com.cn.cloudpictureplatform.domain.space.SpaceType type);

    Optional<Space> findByTeamId(UUID teamId);

    List<Space> findByTeamIdIn(List<UUID> teamIds);

    @Modifying
    @Query("UPDATE Space s SET s.usedBytes = s.usedBytes + :bytes WHERE s.id = :id")
    void incrementUsedBytes(@Param("id") UUID id, @Param("bytes") long bytes);
}
