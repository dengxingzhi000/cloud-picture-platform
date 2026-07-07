package com.cn.cloudpictureplatform.rag.interfaces;

import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.rag.application.IngestionService;
import com.cn.cloudpictureplatform.rag.application.QaService;
import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import com.cn.cloudpictureplatform.rag.interfaces.dto.DocumentUploadResponse;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryRequest;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/markdown"
    );

    private final IngestionService ingestionService;
    private final QaService qaService;
    private final RagProperties ragProperties;

    @PostMapping("/documents/upload")
    public ApiResponse<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {

        // Validate file size
        long maxFileSize = ragProperties.upload() != null ? ragProperties.upload().maxFileSize() : 52428800L;
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + maxFileSize + " bytes");
        }

        // Validate content type
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                "Unsupported file type: " + file.getContentType()
                + ". Allowed types: " + ALLOWED_CONTENT_TYPES
            );
        }

        // Sanitize filename: remove path separators
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            originalFilename = originalFilename.replace("\\", "/").replaceAll(".*/", "");
        }

        RagDocument doc = ingestionService.createAndParseDocument(
            file.getInputStream(),
            originalFilename,
            file.getContentType()
        );

        // Index to OpenSearch outside transaction
        ingestionService.indexToOpenSearch(doc);
        return ApiResponse.ok(new DocumentUploadResponse(
            doc.getId(),
            doc.getTitle(),
            doc.getStatus().name(),
            doc.getChunks() != null ? doc.getChunks().size() : 0
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
            @RequestParam @NotBlank @Size(max = 100) String sessionId) {
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
