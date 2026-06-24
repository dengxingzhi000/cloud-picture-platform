package com.cn.cloudpictureplatform.interfaces.admin;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.rbac.UserRoleService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.UserRoleAssignRequest;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.UserRoleBulkUpdateRequest;
import com.cn.cloudpictureplatform.application.shared.dto.UserRoleResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/roles")
@RequiredArgsConstructor
public class AdminUserRoleController {
    private final UserRoleService userRoleService;

    @GetMapping
    public ApiResponse<List<UserRoleResponse>> getUserRoles(@PathVariable("userId") UUID userId) {
        return ApiResponse.ok(userRoleService.getUserRoles(userId));
    }

    @PostMapping
    public ApiResponse<UserRoleResponse> assignRole(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserRoleAssignRequest request
    ) {
        return ApiResponse.ok(userRoleService.assignRole(userId, request.getRoleId()));
    }

    @DeleteMapping("/{roleId}")
    public ApiResponse<Void> removeRole(
            @PathVariable("userId") UUID userId,
            @PathVariable("roleId") UUID roleId
    ) {
        userRoleService.removeRole(userId, roleId);
        return ApiResponse.ok();
    }

    @PutMapping
    public ApiResponse<Void> updateUserRoles(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserRoleBulkUpdateRequest request
    ) {
        userRoleService.updateUserRoles(userId, request.getRoleIds());
        return ApiResponse.ok();
    }
}
