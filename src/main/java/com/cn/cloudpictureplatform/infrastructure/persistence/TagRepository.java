package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.picture.Tag;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByNameIgnoreCase(String name);

    Page<Tag> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
