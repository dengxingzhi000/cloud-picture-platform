package com.cn.cloudpictureplatform.interfaces.admin.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserSummary(
        UUID id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        String status,
        Set<String> roles,
        Instant createdAt
) {}
