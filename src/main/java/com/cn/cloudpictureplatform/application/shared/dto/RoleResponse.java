package com.cn.cloudpictureplatform.application.shared.dto;

import java.time.Instant;
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
public class RoleResponse {
    private UUID id;
    private String name;
    private String description;
    private boolean isSystem;
    private Set<String> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}
