package com.cn.cloudpictureplatform.rag.interfaces;

import com.cn.cloudpictureplatform.rag.application.IngestionService;
import com.cn.cloudpictureplatform.rag.application.QaService;
import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.DocumentStatus;
import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryRequest;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagControllerTests {

    @Mock
    private QaService qaService;

    @Mock
    private IngestionService ingestionService;

    @Mock
    private RagProperties ragProperties;

    @InjectMocks
    private RagController ragController;

    @BeforeEach
    void setUp() {
        lenient().when(ragProperties.upload()).thenReturn(
            new RagProperties.Upload(52428800L, List.of("application/pdf", "text/plain"))
        );
    }

    @Test
    void shouldReturnQueryResponse() {
        when(qaService.ask(anyString())).thenReturn(
            new QaService.QaResponse("Test answer", List.of("Doc1"), 3)
        );

        var response = ragController.query(new QueryRequest("test question"));

        assertNotNull(response);
        assertEquals("Test answer", response.getData().answer());
        verify(qaService).ask("test question");
    }

    @Test
    void shouldRejectUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.exe",
            "application/octet-stream",
            "content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> ragController.uploadDocument(file));
    }

    @Test
    void shouldRejectOversizedFile() {
        byte[] largeContent = new byte[60 * 1024 * 1024]; // 60MB
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            largeContent
        );

        assertThrows(IllegalArgumentException.class, () -> ragController.uploadDocument(file));
    }

    @Test
    void shouldSanitizeFileNameWithPathSeparators() throws IOException {
        RagDocument doc = new RagDocument();
        doc.setId(UUID.randomUUID());
        doc.setTitle("test.pdf");
        doc.setStatus(DocumentStatus.PROCESSING);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "uploads/test.pdf",
            "application/pdf",
            "content".getBytes()
        );

        when(ingestionService.createAndParseDocument(any(), eq("test.pdf"), eq("application/pdf")))
            .thenReturn(doc);

        ragController.uploadDocument(file);

        verify(ingestionService).createAndParseDocument(any(), eq("test.pdf"), eq("application/pdf"));
    }

    @Test
    void shouldSanitizeFileNameWithBackslashSeparators() throws IOException {
        RagDocument doc = new RagDocument();
        doc.setId(UUID.randomUUID());
        doc.setTitle("test.pdf");
        doc.setStatus(DocumentStatus.PROCESSING);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "C:\\Users\\test.pdf",
            "application/pdf",
            "content".getBytes()
        );

        when(ingestionService.createAndParseDocument(any(), eq("test.pdf"), eq("application/pdf")))
            .thenReturn(doc);

        ragController.uploadDocument(file);

        verify(ingestionService).createAndParseDocument(any(), eq("test.pdf"), eq("application/pdf"));
    }
}
