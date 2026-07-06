package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QwenEmbeddingClientTests {

    @Test
    void shouldHaveCorrectInterface() {
        assertTrue(EmbeddingClient.class.isInterface());
    }

    @Test
    void shouldCreateRequestWithCorrectDimensions() {
        var request = new EmbeddingRequest(List.of("hello world"), "text-embedding-v3", 1024);
        assertEquals(1, request.input().size());
        assertEquals(1024, request.dimensions());
    }
}