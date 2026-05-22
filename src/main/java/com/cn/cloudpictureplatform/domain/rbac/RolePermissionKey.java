package com.cn.cloudpictureplatform.domain.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class RolePermissionKey implements Serializable {
    @Column(name = "role_id", columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "permission_id", columnDefinition = "uuid")
    private UUID permissionId;
}
