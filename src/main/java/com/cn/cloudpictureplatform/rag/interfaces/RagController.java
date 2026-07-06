package com.cn.cloudpictureplatform.rag.interfaces;

import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.rag.application.IngestionService;
import com.cn.cloudpictureplatform.rag.application.QaService;
import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import com.cn.cloudpictureplatform.rag.interfaces.dto.DocumentUploadResponse;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryRequest;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final IngestionService ingestionService;
    private final QaService qaService;

    @PostMapping("/documents/upload")
    public ApiResponse<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {
        RagDocument doc = ingestionService.ingestDocument(
            file.getInputStream(),
            file.getOriginalFilename(),
            file.getContentType()
        );
        return ApiResponse.ok(new DocumentUploadResponse(
            doc.getId(),
            doc.getTitle(),
            doc.getStatus().name(),
            doc.getChunks().size()
        ));
    }

    @PostMapping("/query")
    public ApiResponse<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        QaService.QaResponse qaResponse = qaService.ask(request.query());
        return ApiResponse.ok(new QueryResponse(
            qaResponse.answer(),
            qaResponse.citations(),
            qaResponse.chunksUsed()
        ));
    }

    @PostMapping("/chat")
    public ApiResponse<QueryResponse> chat(
            @Valid @RequestBody QueryRequest request,
            @RequestParam(required = false) String sessionId) {
        QaService.QaResponse qaResponse = qaService.ask(request.query(), sessionId);
        return ApiResponse.ok(new QueryResponse(
            qaResponse.answer(),
            qaResponse.citations(),
            qaResponse.chunksUsed()
        ));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id) {
        ingestionService.softDeleteDocument(id);
        return ApiResponse.ok();
    }
}
