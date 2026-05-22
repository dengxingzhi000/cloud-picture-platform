package com.cn.cloudpictureplatform.infrastructure.persistence;

import com.cn.cloudpictureplatform.domain.rbac.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, UUID> {

    @Query("""
        SELECT DISTINCT m FROM Menu m
        JOIN RoleMenu rm ON rm.menuId = m.id
        WHERE rm.roleId IN :roleIds
        AND m.isVisible = true
        ORDER BY m.sortOrder ASC
    """)
    List<Menu> findByRoleIds(@Param("roleIds") List<UUID> roleIds);

    List<Menu> findByParentIdIsNullOrderBySortOrderAsc();

    Optional<Menu> findByCode(String code);
}