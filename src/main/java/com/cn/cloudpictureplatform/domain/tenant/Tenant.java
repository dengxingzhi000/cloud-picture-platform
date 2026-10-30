package com.cn.cloudpictureplatform.domain.tenant;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "tenant",
        indexes = {
                @Index(name = "idx_tenant_slug", columnList = "slug", unique = true)
        }
)
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 30)
    @Builder.Default
    private TenantPlan plan = TenantPlan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;
}
