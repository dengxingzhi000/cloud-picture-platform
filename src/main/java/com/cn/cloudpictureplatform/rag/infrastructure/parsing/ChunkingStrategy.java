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
            int end = endOfChunk(trimmed, start);
            String chunkText = trimmed.substring(start, end);
            if (!chunkText.isBlank()) {
                chunks.add(new TextChunk(chunkIndex++, chunkText, estimateTokens(chunkText), start, end));
            }
            if (end >= trimmed.length()) {
                break;
            }
            int nextStart = overlapStart(trimmed, start, end);
            start = nextStart <= start ? end : nextStart;
        }

        return chunks;
    }

    /**
     * Exclusive end offset of the chunk starting at {@code start}. The budget is enforced with
     * the same token estimator used for the whole-text check, so the size limit and the chunk
     * boundaries are expressed in one unit.
     */
    private int endOfChunk(String text, int start) {
        int end = start;
        int cjk = 0;
        int other = 0;
        while (end < text.length()) {
            char c = text.charAt(end);
            boolean isCjk = isCjk(c);
            if (isCjk) {
                cjk++;
            } else {
                other++;
            }
            if (cjk + (other + 3) / 4 > maxTokens) {
                if (isCjk) {
                    cjk--;
                } else {
                    other--;
                }
                break;
            }
            end++;
        }
        return Math.max(end, start + 1);
    }

    /**
     * Start offset of the next chunk, keeping roughly {@link #overlapTokens} tokens of the
     * previous chunk so that context spans chunk boundaries.
     */
    private int overlapStart(String text, int start, int end) {
        int pos = end;
        int cjk = 0;
        int other = 0;
        while (pos > start) {
            char c = text.charAt(pos - 1);
            boolean isCjk = isCjk(c);
            if (isCjk) {
                cjk++;
            } else {
                other++;
            }
            if (cjk + (other + 3) / 4 > overlapTokens) {
                if (isCjk) {
                    cjk--;
                } else {
                    other--;
                }
                break;
            }
            pos--;
        }
        return pos;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjkCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjkCount++;
            } else {
                otherCount++;
            }
        }
        return cjkCount + (otherCount + 3) / 4;
    }

    private static boolean isCjk(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
            || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
            || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA;
    }
}
