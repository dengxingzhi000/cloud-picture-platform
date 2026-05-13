package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID>, JpaSpecificationExecutor<TeamMember> {
    List<TeamMember> findByUserIdAndStatus(UUID userId, TeamMemberStatus status);

    List<TeamMember> findByTeamIdAndStatus(UUID teamId, TeamMemberStatus status);

    List<TeamMember> findByTeamId(UUID teamId);

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    long countByTeamIdAndStatus(UUID teamId, TeamMemberStatus status);

    @Query("SELECT tm.teamId AS teamId, COUNT(tm) AS cnt FROM TeamMember tm WHERE tm.teamId IN :teamIds AND tm.status = :status GROUP BY tm.teamId")
    List<TeamMemberCount> countByTeamIdInAndStatus(@Param("teamIds") List<UUID> teamIds, @Param("status") TeamMemberStatus status);

    interface TeamMemberCount {
        UUID getTeamId();
        long getCnt();
    }
}
