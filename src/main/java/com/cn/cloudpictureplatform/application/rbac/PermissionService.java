package com.cn.cloudpictureplatform.application.rbac;

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
import com.cn.cloudpictureplatform.infrastructure.persistence.PermissionRepository;
import com.cn.cloudpictureplatform.application.rbac.dto.PermissionCreateRequest;
import com.cn.cloudpictureplatform.application.shared.dto.PermissionResponse;
import com.cn.cloudpictureplatform.application.rbac.dto.PermissionUpdateRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionRepository permissionRepository;

    @Transactional
    public PermissionResponse createPermission(PermissionCreateRequest request) {
        if (permissionRepository.existsByName(request.getName())) {
            throw new ApiException(ApiErrorCode.PERMISSION_ALREADY_EXISTS,
                    "Permission name already exists: " + request.getName());
        }

        if (permissionRepository.existsByResourceAndAction(request.getResource(), request.getAction())) {
            throw new ApiException(ApiErrorCode.PERMISSION_ALREADY_EXISTS,
                    "Permission already exists for resource: " + request.getResource() + " and action: " + request.getAction());
        }

        Permission permission = Permission.builder()
                .name(request.getName())
                .description(request.getDescription())
                .resource(request.getResource())
                .action(request.getAction())
                .isSystem(false)
                .build();

        permission = permissionRepository.save(permission);
        return mapToResponse(permission);
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermission(UUID permissionId) {
        Permission permission = findPermissionById(permissionId);
        return mapToResponse(permission);
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> listPermissions(int page, int size, String resource) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("resource").ascending().and(Sort.by("action").ascending()));
        Page<Permission> permissionPage;

        if (resource != null && !resource.isEmpty()) {
            permissionPage = permissionRepository.findByResource(resource, pageRequest);
        } else {
            permissionPage = permissionRepository.findAll(pageRequest);
        }

        return new PageResponse<>(
                permissionPage.getContent().stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()),
                permissionPage.getTotalElements(),
                permissionPage.getNumber(),
                permissionPage.getSize()
        );
    }

    @Transactional
    public PermissionResponse updatePermission(UUID permissionId, PermissionUpdateRequest request) {
        Permission permission = findPermissionById(permissionId);

        if (permission.getIsSystem()) {
            throw new ApiException(ApiErrorCode.CANNOT_DELETE_SYSTEM_PERMISSION,
                    "Cannot modify system permission: " + permission.getName());
        }

        if (request.getName() != null && !request.getName().equals(permission.getName())) {
            if (permissionRepository.existsByName(request.getName())) {
                throw new ApiException(ApiErrorCode.PERMISSION_ALREADY_EXISTS,
                        "Permission name already exists: " + request.getName());
            }
            permission.setName(request.getName());
        }

        if (request.getDescription() != null) {
            permission.setDescription(request.getDescription());
        }

        if (request.getResource() != null) {
            permission.setResource(request.getResource());
        }

        if (request.getAction() != null) {
            permission.setAction(request.getAction());
        }

        permission = permissionRepository.save(permission);
        return mapToResponse(permission);
    }

    @Transactional
    public void deletePermission(UUID permissionId) {
        Permission permission = findPermissionById(permissionId);

        if (permission.getIsSystem()) {
            throw new ApiException(ApiErrorCode.CANNOT_DELETE_SYSTEM_PERMISSION,
                    "Cannot delete system permission: " + permission.getName());
        }

        permissionRepository.delete(permission);
    }

    private Permission findPermissionById(UUID permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PERMISSION_NOT_FOUND,
                        "Permission not found: " + permissionId));
    }

    private PermissionResponse mapToResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .description(permission.getDescription())
                .resource(permission.getResource())
                .action(permission.getAction())
                .isSystem(permission.getIsSystem())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}
