package com.cn.cloudpictureplatform.interfaces.auth.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private UUID userId;
    private String username;
    private String token;
    private Instant expiresAt;
}
