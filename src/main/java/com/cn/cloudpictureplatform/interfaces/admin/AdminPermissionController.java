package com.cn.cloudpictureplatform.interfaces.admin;

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
import com.cn.cloudpictureplatform.application.rbac.PermissionService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.PermissionCreateRequest;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.PermissionResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.rbac.PermissionUpdateRequest;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
public class AdminPermissionController {
    private final PermissionService permissionService;

    @GetMapping
    public ApiResponse<PageResponse<PermissionResponse>> listPermissions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String resource
    ) {
        return ApiResponse.ok(permissionService.listPermissions(page, size, resource));
    }

    @GetMapping("/{id}")
    public ApiResponse<PermissionResponse> getPermission(@PathVariable("id") UUID permissionId) {
        return ApiResponse.ok(permissionService.getPermission(permissionId));
    }

    @PostMapping
    public ApiResponse<PermissionResponse> createPermission(@Valid @RequestBody PermissionCreateRequest request) {
        return ApiResponse.ok(permissionService.createPermission(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PermissionResponse> updatePermission(
            @PathVariable("id") UUID permissionId,
            @Valid @RequestBody PermissionUpdateRequest request
    ) {
        return ApiResponse.ok(permissionService.updatePermission(permissionId, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePermission(@PathVariable("id") UUID permissionId) {
        permissionService.deletePermission(permissionId);
        return ApiResponse.ok();
    }
}
