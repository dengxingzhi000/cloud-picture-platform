package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.ChunkStatus;
import com.cn.cloudpictureplatform.rag.domain.DocumentChunk;
import com.cn.cloudpictureplatform.rag.domain.DocumentStatus;
import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import com.cn.cloudpictureplatform.rag.infrastructure.embedding.EmbeddingClient;
import com.cn.cloudpictureplatform.rag.infrastructure.metrics.RagMetrics;
import com.cn.cloudpictureplatform.rag.infrastructure.opensearch.ChunkDocument;
import com.cn.cloudpictureplatform.rag.infrastructure.opensearch.OpenSearchChunkClient;
import com.cn.cloudpictureplatform.rag.infrastructure.parsing.ChunkingStrategy;
import com.cn.cloudpictureplatform.rag.infrastructure.parsing.DocumentParser;
import com.cn.cloudpictureplatform.rag.infrastructure.parsing.ParsedDocument;
import com.cn.cloudpictureplatform.rag.infrastructure.parsing.TextChunk;
import com.cn.cloudpictureplatform.rag.infrastructure.persistence.DocumentChunkRepository;
import com.cn.cloudpictureplatform.rag.infrastructure.persistence.RagDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final RagDocumentRepository ragDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentParser documentParser;
    private final EmbeddingClient embeddingClient;
    private final OpenSearchChunkClient openSearchChunkClient;
    private final RagProperties ragProperties;
    private final RagMetrics ragMetrics;

    @Transactional
    public RagDocument ingestDocument(InputStream inputStream, String filename, String contentType) {
        RagDocument document = new RagDocument();
        document.setTitle(filename);
        document.setOriginalFilename(filename);
        document.setContentType(contentType);
        document.setStatus(DocumentStatus.PROCESSING);
        document = ragDocumentRepository.save(document);

        long start = System.currentTimeMillis();
        try {
            ParsedDocument parsed = documentParser.parse(inputStream, filename, contentType);

            var chunkingConfig = ragProperties.chunking();
            ChunkingStrategy strategy = new ChunkingStrategy(
                chunkingConfig.maxTokens(),
                chunkingConfig.overlapTokens(),
                chunkingConfig.minChunkTokens()
            );

            List<DocumentChunk> allChunks = new ArrayList<>();
            for (ParsedDocument.Page page : parsed.pages()) {
                List<TextChunk> textChunks = strategy.chunk(page.text());
                for (TextChunk tc : textChunks) {
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocument(document);
                    chunk.setChunkIndex(tc.chunkIndex());
                    chunk.setContent(tc.content());
                    chunk.setTokenCount(tc.tokenCount());
                    chunk.setTitle(parsed.title());
                    chunk.setPageNumber(page.pageNumber());
                    chunk.setStatus(ChunkStatus.ACTIVE);
                    allChunks.add(chunk);
                }
            }

            allChunks = documentChunkRepository.saveAll(allChunks);

            var embedConfig = ragProperties.embedding();
            List<String> contents = allChunks.stream().map(DocumentChunk::getContent).toList();
            List<List<Float>> embeddings = embeddingClient.embed(contents, embedConfig.dimensions());

            for (int i = 0; i < allChunks.size(); i++) {
                DocumentChunk chunk = allChunks.get(i);
                String osId = UUID.randomUUID().toString();
                chunk.setOpenSearchId(osId);

                ChunkDocument chunkDoc = new ChunkDocument(
                    document.getId().toString(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    chunk.getTokenCount(),
                    chunk.getTitle(),
                    chunk.getSectionPath(),
                    chunk.getPageNumber() != null ? chunk.getPageNumber() : 0,
                    1.0f,
                    embedConfig.dimensions(),
                    osId,
                    embeddings.get(i)
                );
                openSearchChunkClient.indexChunk(chunkDoc);
            }

            documentChunkRepository.saveAll(allChunks);

            document.setStatus(DocumentStatus.INDEXED);
            long durationMs = System.currentTimeMillis() - start;
            ragMetrics.recordIngestion(allChunks.size(), durationMs);
            return ragDocumentRepository.save(document);

        } catch (Exception e) {
            log.error("Ingestion failed for document {}: {}", filename, e.getMessage());
            document.setStatus(DocumentStatus.FAILED);
            return ragDocumentRepository.save(document);
        }
    }

    @Transactional
    public void softDeleteDocument(UUID documentId) {
        RagDocument doc = ragDocumentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        doc.setDeleted(true);
        ragDocumentRepository.save(doc);
        openSearchChunkClient.deleteByDocumentId(documentId.toString());
    }
}
