package com.cn.cloudpictureplatform.domain.rbac;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "role_menu")
@IdClass(RoleMenuKey.class)
public class RoleMenu {

    @Id
    @Column(name = "role_id", columnDefinition = "uuid", nullable = false)
    private UUID roleId;

    @Id
    @Column(name = "menu_id", columnDefinition = "uuid", nullable = false)
    private UUID menuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", insertable = false, updatable = false)
    private Menu menu;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}