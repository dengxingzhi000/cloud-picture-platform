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
public class TeamSummaryResponse {

    private UUID id;
    private String name;
    private UUID ownerId;
    private UUID spaceId;
    private TeamRole role;
    private long memberCount;
    private Instant createdAt;
}
