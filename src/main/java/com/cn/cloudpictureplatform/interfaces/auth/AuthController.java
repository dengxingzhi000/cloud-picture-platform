package com.cn.cloudpictureplatform.interfaces.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.auth.AuthService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.application.shared.dto.AuthResponse;
import com.cn.cloudpictureplatform.application.shared.dto.LoginResponse;
import com.cn.cloudpictureplatform.application.shared.dto.UserInfoResponse;
import com.cn.cloudpictureplatform.application.shared.dto.UserInfoWithMenusResponse;
import com.cn.cloudpictureplatform.application.auth.dto.LoginRequest;
import com.cn.cloudpictureplatform.application.auth.dto.RegisterRequest;
import com.cn.cloudpictureplatform.application.auth.dto.UserProfileUpdateRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoWithMenusResponse> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ApiResponse.ok(authService.getUserInfoWithMenus(principal.getId(), principal));
    }

    @PatchMapping("/me")
    public ApiResponse<UserInfoResponse> updateMe(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        return ApiResponse.ok(authService.updateProfileAndGetInfo(principal.getId(), request, principal));
    }
}
