package com.cn.cloudpictureplatform.application.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.config.JwtProperties;
import com.cn.cloudpictureplatform.domain.rbac.Role;
import com.cn.cloudpictureplatform.domain.rbac.UserRole;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RolePermissionRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RoleRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.UserRoleRepository;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.infrastructure.security.JwtTokenService;
import com.cn.cloudpictureplatform.interfaces.auth.dto.AuthResponse;
import com.cn.cloudpictureplatform.interfaces.auth.dto.LoginRequest;
import com.cn.cloudpictureplatform.interfaces.auth.dto.RegisterRequest;
import com.cn.cloudpictureplatform.interfaces.auth.dto.UserProfileUpdateRequest;

@Service
public class AuthService {
    private static final String DEFAULT_ROLE_NAME = "ROLE_USER";

    private final AppUserRepository appUserRepository;
    private final SpaceRepository spaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public AuthService(
            AppUserRepository appUserRepository,
            SpaceRepository spaceRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            JwtProperties jwtProperties,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.spaceRepository = spaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (appUserRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "username already exists");
        }
        if (StringUtils.hasText(request.getEmail())
                && appUserRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "email already exists");
        }

        AppUser user = AppUser.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .displayName(StringUtils.hasText(request.getDisplayName())
                        ? request.getDisplayName()
                        : request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .build();
        AppUser saved = appUserRepository.save(user);

        // Assign default USER role
        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE_NAME)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SERVER_ERROR, "default role not found"));
        UserRole userRole = UserRole.builder()
                .userId(saved.getId())
                .roleId(defaultRole.getId())
                .build();
        userRoleRepository.save(userRole);

        // Create personal space
        Space space = Space.builder()
                .ownerId(saved.getId())
                .type(SpaceType.PERSONAL)
                .name(saved.getDisplayName() + " space")
                .build();
        spaceRepository.save(space);

        AppUserPrincipal principal = buildPrincipal(saved);
        String token = jwtTokenService.generateToken(principal);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
        return new AuthResponse(saved.getId(), saved.getUsername(), token, expiresAt);
    }

    public AuthResponse login(LoginRequest request) {
        Optional<AppUser> userOptional = appUserRepository.findByUsername(request.getUsernameOrEmail());
        if (userOptional.isEmpty()) {
            userOptional = appUserRepository.findByEmail(request.getUsernameOrEmail());
        }
        AppUser user = userOptional.orElseThrow(
                () -> new ApiException(ApiErrorCode.UNAUTHORIZED, "invalid credentials")
        );
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "user disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "invalid credentials");
        }
        AppUserPrincipal principal = buildPrincipal(user);
        String token = jwtTokenService.generateToken(principal);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
        return new AuthResponse(user.getId(), user.getUsername(), token, expiresAt);
    }

    @Transactional
    public AppUser updateProfile(UUID userId, UserProfileUpdateRequest request) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "user not found"));
        if (request == null) {
            return user;
        }
        if (StringUtils.hasText(request.getDisplayName())) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getAvatarUrl() != null) {
            String trimmed = request.getAvatarUrl().trim();
            user.setAvatarUrl(StringUtils.hasText(trimmed) ? trimmed : null);
        }
        return appUserRepository.save(user);
    }

    /**
     * Build an AppUserPrincipal with roles and permissions loaded from RBAC tables.
     */
    public AppUserPrincipal buildPrincipal(AppUser user) {
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(user.getId());

        Set<String> roles = roleIds.stream()
                .map(roleRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> permissions = Set.copyOf(
                rolePermissionRepository.findPermissionNamesByRoleIds(roleIds)
        );

        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE,
                roles,
                permissions
        );
    }
}
