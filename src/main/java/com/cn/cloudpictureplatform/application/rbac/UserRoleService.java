package com.cn.cloudpictureplatform.application.rbac;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.rbac.Role;
import com.cn.cloudpictureplatform.domain.rbac.UserRole;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.RoleRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.UserRoleRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.UserRoleResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserRoleService {
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AppUserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserRoleResponse> getUserRoles(UUID userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);

        return userRoles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserRoleResponse assignRole(UUID userId, UUID roleId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "User not found: " + userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleId));

        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new ApiException(ApiErrorCode.USER_ROLE_ALREADY_ASSIGNED,
                    "Role already assigned to user");
        }

        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .build();

        userRole = userRoleRepository.save(userRole);

        return UserRoleResponse.builder()
                .userId(userId)
                .roleId(roleId)
                .roleName(role.getName())
                .roleDescription(role.getDescription())
                .assignedAt(userRole.getCreatedAt())
                .build();
    }

    @Transactional
    public void removeRole(UUID userId, UUID roleId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "User not found: " + userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleId));

        if (role.getName().equals("ROLE_ADMIN")) {
            long adminCount = userRoleRepository.countByRoleId(roleId);
            if (adminCount <= 1) {
                throw new ApiException(ApiErrorCode.CANNOT_REMOVE_LAST_ADMIN,
                        "Cannot remove last admin role");
            }
        }

        if (!userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new ApiException(ApiErrorCode.USER_ROLE_NOT_FOUND,
                    "Role not assigned to user");
        }

        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
    }

    @Transactional
    public void updateUserRoles(UUID userId, Set<UUID> roleIds) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "User not found: " + userId));

        userRoleRepository.deleteByUserId(userId);

        for (UUID roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleId));

            UserRole userRole = UserRole.builder()
                    .userId(userId)
                    .roleId(roleId)
                    .build();

            userRoleRepository.save(userRole);
        }
    }

    private UserRoleResponse mapToResponse(UserRole userRole) {
        Role role = userRole.getRole();
        if (role == null) {
            role = roleRepository.findById(userRole.getRoleId())
                    .orElse(null);
        }

        return UserRoleResponse.builder()
                .userId(userRole.getUserId())
                .roleId(userRole.getRoleId())
                .roleName(role != null ? role.getName() : null)
                .roleDescription(role != null ? role.getDescription() : null)
                .assignedAt(userRole.getCreatedAt())
                .build();
    }
}
