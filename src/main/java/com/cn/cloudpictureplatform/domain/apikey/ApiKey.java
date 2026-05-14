package com.cn.cloudpictureplatform.domain.apikey;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "api_key", indexes = {
        @Index(name = "idx_ak_user", columnList = "user_id")
})
public class ApiKey extends BaseEntity {

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(nullable = false, columnDefinition = "text")
    private String scopes;

    @Column(name = "rate_limit", nullable = false)
    @Builder.Default
    private int rateLimit = 100;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
