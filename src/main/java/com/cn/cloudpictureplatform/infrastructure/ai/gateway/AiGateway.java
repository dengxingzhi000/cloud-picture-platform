package com.cn.cloudpictureplatform.infrastructure.ai.gateway;

import java.util.Optional;
import java.util.UUID;
import com.cn.cloudpictureplatform.infrastructure.ai.dto.AiChatRequest;
import com.cn.cloudpictureplatform.infrastructure.ai.dto.AiChatResponse;

public interface AiGateway {

    Optional<float[]> embedText(String text);

    Optional<float[]> embedImage(String imageUrl);

    void submitTaggingTask(UUID pictureId, String imageUrl);

    void submitModerationTask(UUID pictureId, String imageUrl);

    AiChatResponse chat(AiChatRequest request);
}
