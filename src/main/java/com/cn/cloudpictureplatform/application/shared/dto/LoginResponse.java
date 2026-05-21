package com.cn.cloudpictureplatform.application.shared.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.cn.cloudpictureplatform.application.auth.dto.MenuItemResponse;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Instant expiresAt;
    private UserInfoResponse userInfo;
    private List<MenuItemResponse> menus;
    private List<String> permissions;
}