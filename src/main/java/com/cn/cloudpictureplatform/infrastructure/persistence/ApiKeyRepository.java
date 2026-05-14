package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.apikey.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<ApiKey> findByKeyPrefixAndKeyHash(String keyPrefix, String keyHash);
}
