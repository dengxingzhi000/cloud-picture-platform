package com.cn.cloudpictureplatform.application.outbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.domain.outbox.OutboxEvent;
import com.cn.cloudpictureplatform.domain.outbox.OutboxStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.OutboxEventRepository;

@Service
public class OutboxService {
    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public void writeEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
        outboxEventRepository.save(event);
    }
}
