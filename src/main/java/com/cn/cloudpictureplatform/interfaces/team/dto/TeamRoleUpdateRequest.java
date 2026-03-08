package com.cn.cloudpictureplatform.interfaces.team.dto;

import jakarta.validation.constraints.NotNull;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeamRoleUpdateRequest {

    @NotNull
    private TeamRole role;
}
