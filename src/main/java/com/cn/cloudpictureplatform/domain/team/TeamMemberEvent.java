package com.cn.cloudpictureplatform.domain.team;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
        name = "team_member_event",
        indexes = {
                @Index(name = "idx_team_member_event_team", columnList = "team_id"),
                @Index(name = "idx_team_member_event_user", columnList = "user_id"),
                @Index(name = "idx_team_member_event_actor", columnList = "actor_id"),
                @Index(name = "idx_team_member_event_type", columnList = "type")
        }
)
public class TeamMemberEvent extends BaseEntity {

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "actor_id", columnDefinition = "uuid")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TeamMemberEventType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TeamRole role;

    @Column(length = 1000)
    private String detail;
}
