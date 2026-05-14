package com.cn.cloudpictureplatform.domain.webhook;

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
@Table(name = "webhook_endpoint", indexes = {
        @Index(name = "idx_we_owner", columnList = "owner_id, owner_type")
})
public class WebhookEndpoint extends BaseEntity {

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "owner_type", nullable = false, length = 10)
    private String ownerType;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 100)
    private String secret;

    @Column(nullable = false, columnDefinition = "text")
    private String events;  // JSON array

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
