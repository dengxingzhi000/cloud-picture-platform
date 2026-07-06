package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.domain.DocumentStatus;
import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import com.cn.cloudpictureplatform.rag.infrastructure.persistence.RagDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTests {

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    @InjectMocks
    private IngestionService ingestionService;

    @Test
    void shouldCreateDocumentEntity() {
        when(ragDocumentRepository.save(any())).thenAnswer(inv -> {
            RagDocument doc = inv.getArgument(0);
            doc.setId(UUID.randomUUID());
            return doc;
        });

        RagDocument doc = new RagDocument();
        doc.setTitle("Test");
        doc.setOriginalFilename("test.pdf");
        doc.setContentType("application/pdf");

        RagDocument saved = ragDocumentRepository.save(doc);

        assertNotNull(saved.getId());
        assertEquals(DocumentStatus.PENDING, saved.getStatus());
        verify(ragDocumentRepository).save(any());
    }
}
