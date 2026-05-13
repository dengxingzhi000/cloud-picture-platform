package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.rbac.RolePermission;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleId(UUID roleId);

    @Query("""
            SELECT DISTINCT p.name FROM RolePermission rp
            JOIN Permission p ON p.id = rp.permissionId
            WHERE rp.roleId IN :roleIds
            """)
    List<String> findPermissionNamesByRoleIds(@Param("roleIds") List<UUID> roleIds);

    @Query("""
            SELECT DISTINCT p.name FROM RolePermission rp
            JOIN Permission p ON p.id = rp.permissionId
            JOIN UserRole ur ON ur.roleId = rp.roleId
            WHERE ur.userId = :userId
            """)
    List<String> findPermissionNamesByUserId(@Param("userId") UUID userId);

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.roleId = :roleId AND rp.permissionId = :permissionId")
    void deleteByRoleIdAndPermissionId(@Param("roleId") UUID roleId, @Param("permissionId") UUID permissionId);
}
