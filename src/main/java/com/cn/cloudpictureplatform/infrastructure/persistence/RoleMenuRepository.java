package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.rbac.RoleMenu;
import com.cn.cloudpictureplatform.domain.rbac.RoleMenuKey;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, RoleMenuKey> {
    boolean existsByRoleIdAndMenuId(UUID roleId, UUID menuId);
}
