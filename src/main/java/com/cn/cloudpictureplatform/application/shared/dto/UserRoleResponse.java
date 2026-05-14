package com.cn.cloudpictureplatform.application.shared.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleResponse {
    private UUID userId;
    private UUID roleId;
    private String roleName;
    private String roleDescription;
    private Instant assignedAt;
}
