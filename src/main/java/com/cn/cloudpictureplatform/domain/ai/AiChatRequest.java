package com.cn.cloudpictureplatform.domain.ai;

import java.util.List;
import java.util.UUID;

public record AiChatRequest(
        String sessionId,
        String message,
        List<UUID> contextPictureIds
) {}
