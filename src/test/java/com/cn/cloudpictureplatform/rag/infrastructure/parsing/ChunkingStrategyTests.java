package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChunkingStrategyTests {

    private final ChunkingStrategy strategy = new ChunkingStrategy(100, 10, 20);

    @Test
    void shouldSplitTextIntoChunks() {
        // 1200 ASCII characters ≈ 300 tokens (4 characters ≈ 1 token), well above maxTokens=100
        String text = "A".repeat(1200);
        List<TextChunk> chunks = strategy.chunk(text);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 2);
        chunks.forEach(c -> assertTrue(c.tokenCount() <= 110));
    }

    @Test
    void shouldNotChunkShortText() {
        String text = "Short text";
        List<TextChunk> chunks = strategy.chunk(text);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).chunkIndex());
    }

    @Test
    void shouldNotChunkTextWithinTokenBudget() {
        // 250 ASCII characters ≈ 63 tokens, below maxTokens=100
        String text = "A".repeat(250);
        List<TextChunk> chunks = strategy.chunk(text);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).tokenCount() <= 100);
    }

    @Test
    void shouldAddOverlapBetweenChunks() {
        String text = "Word ".repeat(200);
        List<TextChunk> chunks = strategy.chunk(text);

        assertTrue(chunks.size() > 1);
        // the next chunk starts before the previous one ends
        assertTrue(chunks.get(1).startOffset() < chunks.get(0).endOffset());
    }

    @Test
    void shouldCoverWholeText() {
        String text = "A".repeat(1200);
        List<TextChunk> chunks = strategy.chunk(text);

        assertEquals(0, chunks.get(0).startOffset());
        assertEquals(text.length(), chunks.get(chunks.size() - 1).endOffset());
    }
}
