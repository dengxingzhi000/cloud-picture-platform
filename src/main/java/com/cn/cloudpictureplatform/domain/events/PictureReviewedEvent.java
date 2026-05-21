package com.cn.cloudpictureplatform.domain.events;

import java.util.UUID;

public record PictureReviewedEvent(
        UUID eventId,
        java.time.Instant occurredAt,
        UUID pictureId,
        String pictureName,
        UUID ownerId,
        boolean approved,
        String reason
) implements DomainEvent {

    public PictureReviewedEvent(UUID pictureId, String pictureName, UUID ownerId,
                                boolean approved, String reason) {
        this(DomainEvent.defaultEventId(), DomainEvent.defaultOccurredAt(),
                pictureId, pictureName, ownerId, approved, reason);
    }
}
