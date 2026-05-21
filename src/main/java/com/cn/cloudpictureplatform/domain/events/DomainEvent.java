package com.cn.cloudpictureplatform.domain.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();

    static UUID defaultEventId() {
        return UUID.randomUUID();
    }

    static Instant defaultOccurredAt() {
        return Instant.now();
    }
}