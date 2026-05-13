package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.rbac.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findByResource(String resource);

    Optional<Permission> findByName(String name);

    boolean existsByName(String name);

    boolean existsByResourceAndAction(String resource, String action);

    Page<Permission> findByResource(String resource, Pageable pageable);

    Page<Permission> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
