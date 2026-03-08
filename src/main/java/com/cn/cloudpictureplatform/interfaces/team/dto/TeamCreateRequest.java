package com.cn.cloudpictureplatform.interfaces.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeamCreateRequest {

    @NotBlank
    @Size(max = 80)
    private String name;

    @Size(max = 200)
    private String description;
}
