package com.cn.cloudpictureplatform.interfaces.team.dto;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamInviteSummaryResponse {

    private UUID teamId;
    private String teamName;
    private TeamRole role;
    private Instant invitedAt;
    private UUID invitedBy;
    private String inviterUsername;
    private String inviterDisplayName;
    private String inviterEmail;
    private String inviterAvatarUrl;
}
