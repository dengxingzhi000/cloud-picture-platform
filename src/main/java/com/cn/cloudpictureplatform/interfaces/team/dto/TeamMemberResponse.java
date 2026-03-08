package com.cn.cloudpictureplatform.interfaces.team.dto;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberResponse {

    private UUID userId;
    private String username;
    private String displayName;
    private UUID invitedBy;
    private String inviterUsername;
    private String inviterDisplayName;
    private String inviterEmail;
    private String inviterAvatarUrl;
    private TeamRole role;
    private TeamMemberStatus status;
    private Instant invitedAt;
    private Instant joinedAt;
}
