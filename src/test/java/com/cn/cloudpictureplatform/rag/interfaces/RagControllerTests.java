package com.cn.cloudpictureplatform.rag.interfaces;

import com.cn.cloudpictureplatform.rag.application.QaService;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryRequest;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagControllerTests {

    @Mock
    private QaService qaService;

    @InjectMocks
    private RagController ragController;

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
}
