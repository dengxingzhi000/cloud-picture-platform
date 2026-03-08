package com.cn.cloudpictureplatform.interfaces.team.dto;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.domain.team.TeamMemberEventType;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberEventResponse {

    private UUID id;
    private UUID teamId;
    private UUID userId;
    private String username;
    private String displayName;
    private UUID actorId;
    private String actorUsername;
    private String actorDisplayName;
    private TeamMemberEventType type;
    private TeamRole role;
    private String detail;
    private Instant createdAt;
}
