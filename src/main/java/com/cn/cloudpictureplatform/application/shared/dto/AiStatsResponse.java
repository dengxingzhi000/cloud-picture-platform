package com.cn.cloudpictureplatform.application.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AiStatsResponse {
    private TaggingStats tagging;
    private ModerationStats moderation;
    private EmbeddingStats embedding;

    @Getter @Builder @AllArgsConstructor
    public static class TaggingStats {
        private long totalCalls;
        private long successCalls;
        private double avgLatencyMs;
    }

    @Getter @Builder @AllArgsConstructor
    public static class ModerationStats {
        private long totalCalls;
        private long autoApproved;
        private long autoRejected;
        private double avgConfidence;
    }

    @Getter @Builder @AllArgsConstructor
    public static class EmbeddingStats {
        private long totalCalls;
        private double avgLatencyMs;
    }
}
