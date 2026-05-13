package com.cn.cloudpictureplatform.interfaces.admin.dto.rbac;

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
public class PermissionResponse {

    private UUID id;
    private String name;
    private String description;
    private String resource;
    private String action;
    private boolean isSystem;
    private Instant createdAt;
    private Instant updatedAt;
}
