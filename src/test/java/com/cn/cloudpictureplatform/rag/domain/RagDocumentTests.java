package com.cn.cloudpictureplatform.rag.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagDocumentTests {

    @Test
    void shouldCreateDocumentWithDefaults() {
        RagDocument doc = new RagDocument();
        doc.setTitle("Test Doc");
        doc.setOriginalFilename("test.pdf");

        assertEquals(DocumentStatus.PENDING, doc.getStatus());
        assertEquals(1, doc.getVersion());
        assertNotNull(doc.getChunks());
        assertTrue(doc.getChunks().isEmpty());
    }

    @Test
    void shouldCreateChunkWithDefaults() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent("test content");
        chunk.setChunkIndex(0);

        assertEquals(ChunkStatus.ACTIVE, chunk.getStatus());
        assertEquals(0, chunk.getChunkIndex());
    }
}
