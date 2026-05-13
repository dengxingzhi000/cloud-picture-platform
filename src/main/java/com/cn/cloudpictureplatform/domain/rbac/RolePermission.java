package com.cn.cloudpictureplatform.domain.rbac;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
class RolePermissionKey implements Serializable {
    @Column(name = "role_id", columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "permission_id", columnDefinition = "uuid")
    private UUID permissionId;
}

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "role_permission")
@IdClass(RolePermissionKey.class)
public class RolePermission {

    @Id
    @Column(name = "role_id", columnDefinition = "uuid", nullable = false)
    private UUID roleId;

    @Id
    @Column(name = "permission_id", columnDefinition = "uuid", nullable = false)
    private UUID permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false)
    private Permission permission;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
