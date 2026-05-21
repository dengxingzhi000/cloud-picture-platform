package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record TeamMemberJoinedEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID teamId,
        String teamName,
        UUID userId,
        String username
) implements DomainEvent {

    public TeamMemberJoinedEvent(UUID teamId, String teamName,
                                 UUID userId, String username) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                teamId, teamName, userId, username);
    }
}
