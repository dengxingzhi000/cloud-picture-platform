package com.cn.cloudpictureplatform.domain.ai;

import java.util.Optional;
import java.util.UUID;

public interface AiGateway {

    Optional<float[]> embedText(String text);

    Optional<float[]> embedImage(String imageUrl);

    void submitTaggingTask(UUID pictureId, String imageUrl);

    void submitModerationTask(UUID pictureId, String imageUrl);

    AiChatResponse chat(AiChatRequest request);
}
