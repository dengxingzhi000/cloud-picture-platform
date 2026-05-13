package com.cn.cloudpictureplatform.domain.rbac;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "permission")
public class Permission extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, length = 50)
    private String resource;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;
}
