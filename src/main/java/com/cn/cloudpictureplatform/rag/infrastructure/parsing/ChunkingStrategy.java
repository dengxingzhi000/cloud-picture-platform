package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import java.util.ArrayList;
import java.util.List;

public class ChunkingStrategy {

    private final int maxTokens;
    private final int overlapTokens;
    private final int minChunkTokens;

    public ChunkingStrategy(int maxTokens, int overlapTokens, int minChunkTokens) {
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
        this.minChunkTokens = minChunkTokens;
    }

    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String trimmed = text.trim();
        int totalTokens = estimateTokens(trimmed);
        if (totalTokens <= maxTokens) {
            return List.of(new TextChunk(0, trimmed, totalTokens, 0, trimmed.length()));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int start = 0;

        while (start < trimmed.length()) {
            int end = Math.min(start + maxTokens, trimmed.length());
            String chunkText = trimmed.substring(start, end);
            if (chunkText.isBlank()) {
                start = end;
                continue;
            }
            int tokenCount = estimateTokens(chunkText);

            chunks.add(new TextChunk(chunkIndex++, chunkText, tokenCount, start, end));

            int nextStart = end - overlapTokens;
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    private int estimateTokens(String text) {
        return text.length();
    }
}
