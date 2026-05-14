package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.outbox.OutboxEvent;
import com.cn.cloudpictureplatform.domain.outbox.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt")
    List<OutboxEvent> findPendingEvents(Pageable pageable);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :status, e.processedAt = :processedAt WHERE e.id = :id")
    void markProcessed(@Param("id") UUID id, @Param("status") OutboxStatus status, @Param("processedAt") java.time.Instant processedAt);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.retryCount = e.retryCount + 1 WHERE e.id = :id")
    void incrementRetry(@Param("id") UUID id);
}
