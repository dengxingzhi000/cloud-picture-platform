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
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.interfaces.auth.dto.AuthResponse;
import com.cn.cloudpictureplatform.interfaces.auth.dto.LoginRequest;
import com.cn.cloudpictureplatform.interfaces.auth.dto.RegisterRequest;
import com.cn.cloudpictureplatform.interfaces.auth.dto.UserInfoResponse;
import com.cn.cloudpictureplatform.interfaces.auth.dto.UserProfileUpdateRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AppUserRepository appUserRepository;

    public AuthController(AuthService authService, AppUserRepository appUserRepository) {
        this.authService = authService;
        this.appUserRepository = appUserRepository;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        AppUser user = appUserRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "user not found"));
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .build();
        return ApiResponse.ok(response);
    }

    @PatchMapping("/me")
    public ApiResponse<UserInfoResponse> updateMe(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        AppUser user = authService.updateProfile(principal.getId(), request);
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .build();
        return ApiResponse.ok(response);
    }
}
