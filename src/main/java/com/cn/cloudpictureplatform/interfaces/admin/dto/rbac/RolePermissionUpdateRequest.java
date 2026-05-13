package com.cn.cloudpictureplatform.interfaces.admin.dto.rbac;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RolePermissionUpdateRequest {

    @NotNull
    private Set<UUID> permissionIds;
}
