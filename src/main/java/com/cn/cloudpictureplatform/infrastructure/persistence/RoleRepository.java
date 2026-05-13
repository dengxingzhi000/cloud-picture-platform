package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.rbac.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    Page<Role> findByNameContainingIgnoreCase(String name, Pageable pageable);

    long countByIsSystem(Boolean isSystem);
}
