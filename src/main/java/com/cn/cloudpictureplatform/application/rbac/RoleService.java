package com.cn.cloudpictureplatform.application.rbac;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.rbac.Permission;
import com.cn.cloudpictureplatform.domain.rbac.Role;
import com.cn.cloudpictureplatform.domain.rbac.RolePermission;
import com.cn.cloudpictureplatform.infrastructure.persistence.PermissionRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RolePermissionRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RoleRepository;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.RoleCreateRequest;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.RoleResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.RoleUpdateRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Transactional
    public RoleResponse createRole(RoleCreateRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new ApiException(ApiErrorCode.ROLE_ALREADY_EXISTS, "Role name already exists: " + request.getName());
        }

        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isSystem(false)
                .build();

        role = roleRepository.save(role);
        return mapToResponse(role);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID roleId) {
        Role role = findRoleById(roleId);
        return mapToResponse(role);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> listRoles(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Role> rolePage = roleRepository.findAll(pageRequest);

        return new PageResponse<>(
                rolePage.getContent().stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()),
                rolePage.getTotalElements(),
                rolePage.getNumber(),
                rolePage.getSize()
        );
    }

    @Transactional
    public RoleResponse updateRole(UUID roleId, RoleUpdateRequest request) {
        Role role = findRoleById(roleId);

        if (request.getName() != null && !request.getName().equals(role.getName())) {
            if (roleRepository.existsByName(request.getName())) {
                throw new ApiException(ApiErrorCode.ROLE_ALREADY_EXISTS, "Role name already exists: " + request.getName());
            }
            role.setName(request.getName());
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        role = roleRepository.save(role);
        return mapToResponse(role);
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = findRoleById(roleId);

        if (role.getIsSystem()) {
            throw new ApiException(ApiErrorCode.CANNOT_DELETE_SYSTEM_ROLE, "Cannot delete system role: " + role.getName());
        }

        rolePermissionRepository.deleteByRoleId(roleId);
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public Set<String> getRolePermissions(UUID roleId) {
        Role role = findRoleById(roleId);
        return role.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void updateRolePermissions(UUID roleId, Set<UUID> permissionIds) {
        Role role = findRoleById(roleId);

        rolePermissionRepository.deleteByRoleId(roleId);

        Set<Permission> permissions = new HashSet<>();
        for (UUID permissionId : permissionIds) {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PERMISSION_NOT_FOUND, "Permission not found: " + permissionId));
            permissions.add(permission);

            RolePermission rolePermission = RolePermission.builder()
                    .roleId(roleId)
                    .permissionId(permissionId)
                    .build();
            rolePermissionRepository.save(rolePermission);
        }

        role.setPermissions(permissions);
        roleRepository.save(role);
    }

    private Role findRoleById(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleId));
    }

    private RoleResponse mapToResponse(Role role) {
        Set<String> permissionNames = role.getPermissions() != null
                ? role.getPermissions().stream()
                        .map(Permission::getName)
                        .collect(Collectors.toSet())
                : new HashSet<>();

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .permissions(permissionNames)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
