package com.cn.cloudpictureplatform.application.shared.dto;

import java.util.Set;
import java.util.UUID;
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
    private Set<String> roles;
    private Set<String> permissions;
}
