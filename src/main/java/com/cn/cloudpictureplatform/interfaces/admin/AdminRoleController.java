package com.cn.cloudpictureplatform.interfaces.admin;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.rbac.RoleService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.application.rbac.dto.RoleCreateRequest;
import com.cn.cloudpictureplatform.application.rbac.dto.RoleUpdateRequest;
import com.cn.cloudpictureplatform.application.shared.dto.RoleResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.RolePermissionUpdateRequest;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {
    private final RoleService roleService;

    @GetMapping
    public ApiResponse<PageResponse<RoleResponse>> listRoles(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(roleService.listRoles(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> getRole(@PathVariable("id") UUID roleId) {
        return ApiResponse.ok(roleService.getRole(roleId));
    }

    @PostMapping
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable("id") UUID roleId,
            @Valid @RequestBody RoleUpdateRequest request
    ) {
        return ApiResponse.ok(roleService.updateRole(roleId, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable("id") UUID roleId) {
        roleService.deleteRole(roleId);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<Set<String>> getRolePermissions(@PathVariable("id") UUID roleId) {
        return ApiResponse.ok(roleService.getRolePermissions(roleId));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<Void> updateRolePermissions(
            @PathVariable("id") UUID roleId,
            @Valid @RequestBody RolePermissionUpdateRequest request
    ) {
        roleService.updateRolePermissions(roleId, request.getPermissionIds());
        return ApiResponse.ok();
    }
}
