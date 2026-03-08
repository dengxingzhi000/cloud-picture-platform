package com.cn.cloudpictureplatform.interfaces.auth.dto;

import java.util.UUID;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private UUID userId;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private UserRole role;
}
