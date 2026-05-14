package com.cn.cloudpictureplatform.infrastructure.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiChatRequest(
        String sessionId,
        String message,
        List<UUID> contextPictureIds
) {}
