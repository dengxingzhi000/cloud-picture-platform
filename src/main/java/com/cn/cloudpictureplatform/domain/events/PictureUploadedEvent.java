package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record PictureUploadedEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID pictureId,
        String pictureName,
        UUID ownerId,
        UUID spaceId,
        UUID teamId,
        boolean isPublic
) implements DomainEvent {

    public PictureUploadedEvent(UUID pictureId, String pictureName, UUID ownerId,
                                UUID spaceId, UUID teamId, boolean isPublic) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                pictureId, pictureName, ownerId, spaceId, teamId, isPublic);
    }
}
