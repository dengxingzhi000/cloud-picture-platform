package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.rbac.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId")
    List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);

    Optional<UserRole> findByUserIdAndRoleId(UUID userId, UUID roleId);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);

    void deleteByUserId(UUID userId);

    long countByRoleId(UUID roleId);

    List<UserRole> findByUserIdIn(List<UUID> userIds);
}
