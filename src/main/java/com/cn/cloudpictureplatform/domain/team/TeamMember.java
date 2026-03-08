package com.cn.cloudpictureplatform.domain.team;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "team_member",
        indexes = {
                @Index(name = "idx_team_member_team", columnList = "team_id"),
                @Index(name = "idx_team_member_user", columnList = "user_id"),
                @Index(name = "idx_team_member_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_team_member", columnNames = {"team_id", "user_id"})
        }
)
public class TeamMember extends BaseEntity {

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TeamRole role = TeamRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TeamMemberStatus status = TeamMemberStatus.INVITED;

    @Column(name = "invited_by", columnDefinition = "uuid")
    private UUID invitedBy;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "joined_at")
    private Instant joinedAt;
}
