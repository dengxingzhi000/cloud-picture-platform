package com.cn.cloudpictureplatform.rag.interfaces;

import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.rag.application.IngestionService;
import com.cn.cloudpictureplatform.rag.application.QaService;
import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import com.cn.cloudpictureplatform.rag.interfaces.dto.ChatRequest;
import com.cn.cloudpictureplatform.rag.interfaces.dto.DocumentSummaryResponse;
import com.cn.cloudpictureplatform.rag.interfaces.dto.DocumentUploadResponse;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryRequest;
import com.cn.cloudpictureplatform.rag.interfaces.dto.QueryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.cn.cloudpictureplatform.rag.application.QaService.StreamChunk;

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
            qaResponse.chunksUsed(),
            null
        ));
    }

    @PostMapping("/chat")
    public ApiResponse<QueryResponse> chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        QaService.QaResponse qaResponse = qaService.ask(request.query(), sessionId);
        return ApiResponse.ok(new QueryResponse(
            qaResponse.answer(),
            qaResponse.citations(),
            qaResponse.chunksUsed(),
            sessionId
        ));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        qaService.streamAsk(request.query(), sessionId)
            .doOnNext(chunk -> {
                try {
                    switch (chunk.type()) {
                        case "token" -> emitter.send(SseEmitter.event().name("token").data(chunk.content()));
                        case "meta" -> emitter.send(SseEmitter.event().name("meta").data(new QueryResponse(null, chunk.citations(), chunk.chunksUsed(), sessionId)));
                        case "done" -> emitter.send(SseEmitter.event().name("done").data(""));
                    }
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            })
            .doOnComplete(emitter::complete)
            .doOnError(emitter::completeWithError)
            .subscribe();
        return emitter;
    }

    @GetMapping("/documents")
    public ApiResponse<List<DocumentSummaryResponse>> listDocuments() {
        List<DocumentSummaryResponse> documents = ingestionService.listDocuments();
        return ApiResponse.ok(documents);
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id) {
        ingestionService.softDeleteDocument(id);
        return ApiResponse.ok();
    }
}
