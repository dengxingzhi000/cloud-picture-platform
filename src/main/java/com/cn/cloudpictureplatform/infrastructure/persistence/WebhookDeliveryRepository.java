package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.webhook.WebhookDelivery;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    Page<WebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(UUID webhookId, Pageable pageable);
}
