package com.cn.cloudpictureplatform.interfaces.admin.dto.rbac;

import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleBulkUpdateRequest {

    @NotEmpty
    private Set<UUID> roleIds;
}
