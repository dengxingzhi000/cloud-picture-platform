package com.cn.cloudpictureplatform.interfaces.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeamInviteRequest {

    @NotBlank
    @Size(max = 120)
    private String username;

    private TeamRole role;
}
