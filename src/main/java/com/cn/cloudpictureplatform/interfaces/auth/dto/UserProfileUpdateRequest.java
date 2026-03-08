package com.cn.cloudpictureplatform.interfaces.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserProfileUpdateRequest {

    @Size(max = 80)
    private String displayName;

    @Size(max = 500)
    private String avatarUrl;
}
