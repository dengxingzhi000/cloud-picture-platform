package com.cn.cloudpictureplatform.interfaces.admin.dto.rbac;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleAssignRequest {

    @NotNull
    private UUID roleId;
}
