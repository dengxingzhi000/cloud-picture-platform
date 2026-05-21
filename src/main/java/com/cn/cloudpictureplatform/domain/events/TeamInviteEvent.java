package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record TeamInviteEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID teamId,
        String teamName,
        String inviteeUsername,
        String inviterUsername
) implements DomainEvent {

    public TeamInviteEvent(UUID teamId, String teamName,
                           String inviteeUsername, String inviterUsername) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                teamId, teamName, inviteeUsername, inviterUsername);
    }
}
