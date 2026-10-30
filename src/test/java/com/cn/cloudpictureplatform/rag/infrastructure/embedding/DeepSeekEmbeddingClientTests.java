package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekEmbeddingClientTests {

    @Test
    void shouldHaveCorrectInterface() {
        assertTrue(EmbeddingClient.class.isInterface());
    }

    @Test
    void shouldCreateRequestWithCorrectModel() {
        var request = new EmbeddingRequest(List.of("hello world"), "clip");
        assertEquals(1, request.input().size());
        assertEquals("clip", request.model());
    }
}
