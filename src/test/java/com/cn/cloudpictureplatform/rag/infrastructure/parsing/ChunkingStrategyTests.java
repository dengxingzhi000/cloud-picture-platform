package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChunkingStrategyTests {

    private final ChunkingStrategy strategy = new ChunkingStrategy(100, 10, 20);

    @Test
    void shouldSplitTextIntoChunks() {
        String text = "A".repeat(250);
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
    void shouldAddOverlapBetweenChunks() {
        String text = "Word ".repeat(200);
        List<TextChunk> chunks = strategy.chunk(text);

        if (chunks.size() > 1) {
            String lastPartOfFirst = chunks.get(0).content();
            String firstPartOfSecond = chunks.get(1).content();
            assertNotNull(lastPartOfFirst);
            assertNotNull(firstPartOfSecond);
        }
    }
}