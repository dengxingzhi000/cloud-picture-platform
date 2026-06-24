package com.cn.cloudpictureplatform.domain.ai;

import java.util.List;

public record AiChatResponse(
        String sessionId,
        String reply,
        String intent,
        List<String> suggestedActions
) {}
