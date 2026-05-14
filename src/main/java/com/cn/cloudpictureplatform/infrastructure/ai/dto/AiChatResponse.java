package com.cn.cloudpictureplatform.infrastructure.ai.dto;

import java.util.List;

public record AiChatResponse(
        String sessionId,
        String reply,
        String intent,
        List<String> suggestedActions
) {}
