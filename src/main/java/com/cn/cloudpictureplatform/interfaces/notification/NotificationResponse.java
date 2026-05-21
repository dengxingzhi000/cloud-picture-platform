package com.cn.cloudpictureplatform.interfaces.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String kind,
        String title,
        String body,
        UUID targetId,
        boolean read,
        Instant createdAt
) {}
