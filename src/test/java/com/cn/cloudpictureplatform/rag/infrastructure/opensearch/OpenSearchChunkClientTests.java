package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpenSearchChunkClientTests {

    @Test
    void shouldCreateChunkDocumentCorrectly() {
        var doc = new ChunkDocument(
            "doc-123", 0, "Test content", 10, "Test Title",
            "Chapter 1 > Section 1", 1, 1.0f, 1024,
            "test-opensearch-id",
            List.of(0.1f, 0.2f, 0.3f)
        );
        assertEquals("doc-123", doc.documentId());
        assertEquals(10, doc.tokenCount());
        assertEquals(1024, doc.embeddingDim());
        assertEquals(3, doc.embedding().size());
    }
}