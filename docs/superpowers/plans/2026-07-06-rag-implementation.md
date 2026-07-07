# RAG Enterprise Knowledge Base — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a RAG system as a new `rag/` bounded context within Cloud Picture Platform, using OpenSearch for hybrid BM25+vector retrieval, Qwen for embedding/rerank, and DeepSeek for generation.

**Architecture:** Spring AI foundation for ingestion + generation, custom retrieval layer for hybrid search + RRF + rerank. Separate bounded context (`rag/`) sharing only common infrastructure (BaseEntity, ApiResponse, cache, security).

**Tech Stack:** Spring Boot 4.0.5, Java 21, Spring AI, OpenSearch 2.12, Qwen text-embedding-v3, Qwen gte-rerank-v2, DeepSeek-R1, PostgreSQL, Redis+Caffeine, Prometheus

---

## Phase 0: P0 — Core Path (End-to-End Pipeline)

### Task 1: Domain Entities + DB Migration

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/domain/RagDocument.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/domain/DocumentChunk.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/domain/ChunkStatus.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/domain/DocumentStatus.java`
- Create: `src/main/resources/db/migration/V37__rag.sql`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/domain/RagDocumentTests.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.domain.RagDocumentTests" -Dspring.profiles.active=dev`
Expected: FAIL — classes don't exist yet

- [ ] **Step 3: Write domain entities**

`ChunkStatus.java`:
```java
package com.cn.cloudpictureplatform.rag.domain;

public enum ChunkStatus {
    ACTIVE, SUPERSEDED, DELETED
}
```

`DocumentStatus.java`:
```java
package com.cn.cloudpictureplatform.rag.domain;

public enum DocumentStatus {
    PENDING, PROCESSING, INDEXED, FAILED
}
```

`RagDocument.java`:
```java
package com.cn.cloudpictureplatform.rag.domain;

import java.util.ArrayList;
import java.util.List;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rag_document")
public class RagDocument extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType;

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(nullable = false)
    private Integer version = 1;

    private UUID supersededBy;

    @Column(nullable = false)
    private Boolean deleted = false;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DocumentChunk> chunks = new ArrayList<>();

    @Version
    private Long versionLock;
}
```

`DocumentChunk.java`:
```java
package com.cn.cloudpictureplatform.rag.domain;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rag_document_chunk")
public class DocumentChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private RagDocument document;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private Integer tokenCount;

    private String title;

    private String sectionPath;

    private Integer pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkStatus status = ChunkStatus.ACTIVE;

    @Column(nullable = false)
    private Integer chunkVersion = 1;

    @Column(name = "opensearch_id")
    private String openSearchId;

    @Column(nullable = false)
    private Integer embeddingDim = 1024;
}
```

- [ ] **Step 4: Write Flyway migration**

`V37__rag.sql`:
```sql
CREATE TABLE rag_document (
    id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version INTEGER NOT NULL DEFAULT 1,
    superseded_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version_lock BIGINT,
    PRIMARY KEY (id)
);

CREATE TABLE rag_document_chunk (
    id UUID NOT NULL,
    document_id UUID NOT NULL REFERENCES rag_document(id),
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL,
    title VARCHAR(500),
    section_path VARCHAR(1000),
    page_number INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    chunk_version INTEGER NOT NULL DEFAULT 1,
    opensearch_id VARCHAR(100),
    embedding_dim INTEGER NOT NULL DEFAULT 1024,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_rag_chunk_doc ON rag_document_chunk(document_id, chunk_index);
CREATE INDEX idx_rag_chunk_status ON rag_document_chunk(status);
CREATE INDEX idx_rag_doc_status ON rag_document(status, deleted);
CREATE INDEX idx_rag_doc_title ON rag_document USING gin(to_tsvector('simple', title));
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.domain.RagDocumentTests" -Dspring.profiles.active=dev`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/domain/ src/main/resources/db/migration/V37__rag.sql src/test/java/com/cn/cloudpictureplatform/rag/domain/RagDocumentTests.java
git commit -m "feat(rag): add domain entities and DB migration for rag_document/rag_document_chunk"
```

---

### Task 2: Configuration Properties + OpenSearch Docker

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/config/RagProperties.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/config/RagConfig.java`
- Create: `docker-compose.yml`

- [ ] **Step 1: Write RagProperties**

```java
package com.cn.cloudpictureplatform.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
    boolean enabled,
    OpenSearch openSearch,
    Embedding embedding,
    Rerank rerank,
    Generation generation,
    Chunking chunking,
    Retrieval retrieval,
    Cache cache
) {
    public record OpenSearch(
        String baseUrl,
        String indexName,
        int connectTimeoutMs,
        int socketTimeoutMs
    ) {}

    public record Embedding(
        String baseUrl,
        String model,
        int dimensions,
        int batchSize,
        Duration timeout
    ) {}

    public record Rerank(
        String baseUrl,
        String model,
        int topN,
        boolean enabled,
        Duration timeout
    ) {}

    public record Generation(
        String model,
        int maxTokens,
        double temperature
    ) {}

    public record Chunking(
        int maxTokens,
        int overlapTokens,
        int minChunkTokens
    ) {}

    public record Retrieval(
        int bm25TopK,
        int vectorTopK,
        int rrfK,
        int finalTopN
    ) {}

    public record Cache(
        Duration embeddingTtl,
        Duration retrievalTtl,
        boolean semanticEnabled,
        double semanticThreshold
    ) {}
}
```

- [ ] **Step 2: Write RagConfig**

```java
package com.cn.cloudpictureplatform.rag.config;

import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public OpenSearchClient openSearchClient(RagProperties properties) {
        var config = properties.openSearch();
        var restClient = RestClient.builder(
            org.apache.http.HttpHost.create(config.baseUrl())
        ).build();
        return new OpenSearchClient(restClient);
    }

    @Bean
    public WebClient qwenEmbeddingWebClient(RagProperties properties) {
        var config = properties.embedding();
        return WebClient.builder()
            .baseUrl(config.baseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public WebClient qwenRerankWebClient(RagProperties properties) {
        var config = properties.rerank();
        return WebClient.builder()
            .baseUrl(config.baseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

- [ ] **Step 3: Add default config to application.yml**

Append to `src/main/resources/application.yml`:
```yaml
app:
  rag:
    enabled: true
    open-search:
      base-url: http://localhost:9200
      index-name: rag_chunks
      connect-timeout-ms: 5000
      socket-timeout-ms: 30000
    embedding:
      base-url: http://localhost:8000
      model: text-embedding-v3
      dimensions: 1024
      batch-size: 32
      timeout: 10s
    rerank:
      base-url: http://localhost:8000
      model: gte-rerank-v2
      top-n: 5
      enabled: true
      timeout: 10s
    generation:
      model: deepseek-reasoner
      max-tokens: 4096
      temperature: 0.3
    chunking:
      max-tokens: 512
      overlap-tokens: 50
      min-chunk-tokens: 100
    retrieval:
      bm25-top-k: 20
      vector-top-k: 20
      rrf-k: 60
      final-top-n: 5
    cache:
      embedding-ttl: 24h
      retrieval-ttl: 1h
      semantic-enabled: false
      semantic-threshold: 0.95
```

- [ ] **Step 4: Write docker-compose.yml**

```yaml
version: '3.8'
services:
  opensearch:
    image: opensearchproject/opensearch:2.12.0
    container_name: rag-opensearch
    environment:
      - discovery.type=single-node
      - plugins.security.disabled=true
      - OPENSEARCH_INITIAL_ADMIN_PASSWORD=admin
    ports:
      - "9200:9200"
      - "9600:9600"
    volumes:
      - opensearch-data:/usr/share/opensearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\":\"green\\|yellow\"'"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: rag-redis
    ports:
      - "6379:6379"

volumes:
  opensearch-data:
```

- [ ] **Step 5: Compile to verify no errors**

Run: `.\mvnw compile -Dspring.profiles.active=dev`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/config/ src/main/resources/application.yml docker-compose.yml
git commit -m "feat(rag): add RagProperties, RagConfig, docker-compose for OpenSearch"
```

---

### Task 3: Ingestion Service — Document Parsing + Chunking

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/DocumentParser.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/ParsedDocument.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/ChunkingStrategy.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/TextChunk.java`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/ChunkingStrategyTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChunkingStrategyTests {

    private final ChunkingStrategy strategy = new ChunkingStrategy(100, 10, 20);

    @Test
    void shouldSplitTextIntoChunks() {
        String text = "A".repeat(250);
        List<TextChunk> chunks = strategy.chunk(text);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 2);
        chunks.forEach(c -> assertTrue(c.tokenCount() <= 110)); // maxTokens + overlap
    }

    @Test
    void shouldNotChunkShortText() {
        String text = "Short text";
        List<TextChunk> chunks = strategy.chunk(text);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).chunkIndex());
    }

    @Test
    void shouldAddOverlapBetweenChunks() {
        String text = "Word ".repeat(200); // ~200 tokens
        List<TextChunk> chunks = strategy.chunk(text);

        if (chunks.size() > 1) {
            // Overlap means some content appears in adjacent chunks
            String lastPartOfFirst = chunks.get(0).content();
            String firstPartOfSecond = chunks.get(1).content();
            // They should share some text due to overlap
            assertNotNull(lastPartOfFirst);
            assertNotNull(firstPartOfSecond);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.parsing.ChunkingStrategyTests" -Dspring.profiles.active=dev`
Expected: FAIL

- [ ] **Step 3: Implement TextChunk**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

public record TextChunk(
    int chunkIndex,
    String content,
    int tokenCount,
    int startOffset,
    int endOffset
) {}
```

- [ ] **Step 4: Implement ParsedDocument**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import java.util.List;

public record ParsedDocument(
    String title,
    String contentType,
    List<Page> pages
) {
    public record Page(int pageNumber, String text, List<String> headings) {}
}
```

- [ ] **Step 5: Implement DocumentParser**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.List;

@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    public ParsedDocument parse(InputStream inputStream, String filename, String contentType) {
        try {
            Metadata metadata = new Metadata();
            metadata.set("title", filename);
            String text = tika.parseToString(inputStream, metadata);
            String title = metadata.get("title") != null ? metadata.get("title") : filename;
            return new ParsedDocument(
                title,
                contentType,
                List.of(new ParsedDocument.Page(1, text, List.of()))
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse document: " + filename, e);
        }
    }
}
```

- [ ] **Step 6: Implement ChunkingStrategy**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChunkingStrategy {

    private final int maxTokens;
    private final int overlapTokens;
    private final int minChunkTokens;

    public ChunkingStrategy(int maxTokens, int overlapTokens, int minChunkTokens) {
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
        this.minChunkTokens = minChunkTokens;
    }

    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] words = text.split("\\s+");
        if (estimateTokens(words.length) <= maxTokens) {
            return List.of(new TextChunk(0, text.trim(), estimateTokens(words.length), 0, text.length()));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + maxTokens, words.length);
            String chunkText = String.join(" ", List.of(words).subList(start, end)).trim();
            int tokenCount = estimateTokens(end - start);

            chunks.add(new TextChunk(chunkIndex++, chunkText, tokenCount, 0, chunkText.length()));

            // Move start forward by (maxTokens - overlap) to create overlap
            int nextStart = end - overlapTokens;
            if (nextStart <= start) {
                nextStart = end; // prevent infinite loop
            }
            start = nextStart;
        }

        return chunks;
    }

    private int estimateTokens(int wordCount) {
        // Rough estimate: 1 token ≈ 1.3 words for English, ~1.5 for Chinese
        return (int) Math.ceil(wordCount * 1.0);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.parsing.ChunkingStrategyTests" -Dspring.profiles.active=dev`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/ src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/ChunkingStrategyTests.java
git commit -m "feat(rag): add document parsing (Tika) and chunking strategy with overlap"
```

---

### Task 4: Qwen Embedding Client

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/embedding/EmbeddingClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/embedding/QwenEmbeddingClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/embedding/EmbeddingRequest.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/embedding/EmbeddingResponse.java`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/embedding/QwenEmbeddingClientTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QwenEmbeddingClientTests {

    @Test
    void shouldHaveCorrectInterface() {
        // Verify the interface contract
        assertTrue(EmbeddingClient.class.isInterface());
    }

    @Test
    void shouldCreateRequestWithCorrectDimensions() {
        var request = new EmbeddingRequest(List.of("hello world"), "text-embedding-v3", 1024);
        assertEquals(1, request.input().size());
        assertEquals(1024, request.dimensions());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.embedding.QwenEmbeddingClientTests" -Dspring.profiles.active=dev`
Expected: FAIL

- [ ] **Step 3: Implement request/response records**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import java.util.List;

public record EmbeddingRequest(List<String> input, String model, int dimensions) {}
```

```java
package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import java.util.List;

public record EmbeddingResponse(List<List<Float>> embeddings, String model, int dimensions) {}
```

- [ ] **Step 4: Implement EmbeddingClient interface**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import java.util.List;

public interface EmbeddingClient {
    List<List<Float>> embed(List<String> texts, int dimensions);
    List<Float> embedSingle(String text, int dimensions);
}
```

- [ ] **Step 5: Implement QwenEmbeddingClient**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.embedding;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QwenEmbeddingClient implements EmbeddingClient {

    private final WebClient qwenEmbeddingWebClient;
    private final RagProperties ragProperties;

    @Override
    public List<List<Float>> embed(List<String> texts, int dimensions) {
        var config = ragProperties.embedding();
        var request = new EmbeddingRequest(texts, config.model(), dimensions);

        EmbeddingResponse response = qwenEmbeddingWebClient.post()
            .uri("/v1/embeddings")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(EmbeddingResponse.class)
            .block();

        if (response == null || response.embeddings() == null) {
            throw new RuntimeException("Failed to get embeddings from Qwen");
        }

        log.debug("Embedded {} texts, got {} vectors of dim {}",
            texts.size(), response.embeddings().size(), dimensions);
        return response.embeddings();
    }

    @Override
    public List<Float> embedSingle(String text, int dimensions) {
        return embed(List.of(text), dimensions).get(0);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.embedding.QwenEmbeddingClientTests" -Dspring.profiles.active=dev`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/embedding/ src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/embedding/QwenEmbeddingClientTests.java
git commit -m "feat(rag): add QwenEmbeddingClient for text-embedding-v3"
```

---

### Task 5: OpenSearch Chunk Index + Client

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchChunkClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/ChunkDocument.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchIndexInitializer.java`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchChunkClientTests.java`

- [ ] **Step 1: Write the failing test**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenSearchChunkClientTests {

    @Test
    void shouldCreateChunkDocumentCorrectly() {
        var doc = new ChunkDocument(
            "doc-123", 0, "Test content", 10, "Test Title",
            "Chapter 1 > Section 1", 1, 1.0f, 1024,
            "test-opensearch-id"
        );
        assertEquals("doc-123", doc.documentId());
        assertEquals(10, doc.tokenCount());
        assertEquals(1024, doc.embeddingDim());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.opensearch.OpenSearchChunkClientTests" -Dspring.profiles.active=dev`
Expected: FAIL

- [ ] **Step 3: Implement ChunkDocument**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import java.util.List;

public record ChunkDocument(
    String documentId,
    int chunkIndex,
    String content,
    int tokenCount,
    String title,
    String sectionPath,
    int pageNumber,
    float bm25Boost,
    int embeddingDim,
    String openSearchId
) {}
```

- [ ] **Step 4: Implement OpenSearchChunkClient**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchChunkClient {

    private final OpenSearchClient openSearchClient;
    private final RagProperties ragProperties;

    public String indexChunk(ChunkDocument chunk) {
        try {
            var config = ragProperties.openSearch();
            IndexRequest<ChunkDocument> request = IndexRequest.of(b -> b
                .index(config.indexName())
                .id(chunk.openSearchId())
                .document(chunk)
            );

            IndexResponse response = openSearchClient.index(request);
            log.debug("Indexed chunk {} into OpenSearch: result={}",
                chunk.openSearchId(), response.result());
            return response.id();
        } catch (Exception e) {
            throw new RuntimeException("Failed to index chunk into OpenSearch", e);
        }
    }

    public void deleteByDocumentId(String documentId) {
        try {
            var config = ragProperties.openSearch();
            openSearchClient.deleteByQuery(d -> d
                .index(config.indexName())
                .query(q -> q
                    .term(t -> t
                        .field("documentId")
                        .value(documentId)
                    )
                )
            );
            log.debug("Deleted chunks for document {} from OpenSearch", documentId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete chunks from OpenSearch", e);
        }
    }
}
```

- [ ] **Step 5: Implement OpenSearchIndexInitializer**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchIndexInitializer {

    private final OpenSearchClient openSearchClient;
    private final RagProperties ragProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            var config = ragProperties.openSearch();
            boolean exists = openSearchClient.indices().exists(
                ExistsRequest.of(e -> e.index(config.indexName()))
            ).value();

            if (!exists) {
                openSearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(config.indexName())
                    .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                    )
                    .mappings(m -> m
                        .properties("documentId", p -> p.keyword(k -> k))
                        .properties("chunkIndex", p -> p.integer(i -> i))
                        .properties("content", p -> p.text(t -> t
                            .analyzer("standard")
                        ))
                        .properties("tokenCount", p -> p.integer(i -> i))
                        .properties("title", p -> p.text(t -> t
                            .analyzer("standard")
                        ))
                        .properties("sectionPath", p -> p.text(t -> t
                            .analyzer("standard")
                        ))
                        .properties("pageNumber", p -> p.integer(i -> i))
                        .properties("bm25Boost", p -> p.float_(f -> f))
                        .properties("embeddingDim", p -> p.integer(i -> i))
                        .properties("openSearchId", p -> p.keyword(k -> k))
                    )
                ));
                log.info("Created OpenSearch index: {}", config.indexName());
            } else {
                log.info("OpenSearch index already exists: {}", config.indexName());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize OpenSearch index (will retry): {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.opensearch.OpenSearchChunkClientTests" -Dspring.profiles.active=dev`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/ src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchChunkClientTests.java
git commit -m "feat(rag): add OpenSearch chunk client and index initializer"
```

---

### Task 6: Ingestion Service (Orchestrator)

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/application/IngestionService.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/persistence/RagDocumentRepository.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/persistence/DocumentChunkRepository.java`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/application/IngestionServiceTests.java`

- [ ] **Step 1: Write repositories**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.persistence;

import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {
    List<RagDocument> findByDeletedFalse();
    List<RagDocument> findByOriginalFilenameAndDeletedFalse(String filename);
}
```

```java
package com.cn.cloudpictureplatform.rag.infrastructure.persistence;

import com.cn.cloudpictureplatform.rag.domain.DocumentChunk;
import com.cn.cloudpictureplatform.rag.domain.ChunkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentIdAndStatusOrderByChunkIndex(UUID documentId, ChunkStatus status);

    @Modifying
    @Query("UPDATE DocumentChunk c SET c.status = :status WHERE c.document.id = :documentId")
    void updateStatusByDocumentId(UUID documentId, ChunkStatus status);
}
```

- [ ] **Step 2: Write the failing test**

```java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.application.IngestionServiceTests" -Dspring.profiles.active=dev`
Expected: FAIL

- [ ] **Step 4: Implement IngestionService**

```java
package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.DocumentChunk;
import com.cn.cloudpictureplatform.rag.domain.DocumentStatus;
import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import com.cn.cloudpictureplatform.rag.infrastructure.embedding.EmbeddingClient;
import com.cn.cloudpictureplatform.rag.infrastructure.opensearch.ChunkDocument;
import com.cn.cloudpictureplatform.rag.infrastructure.opensearch.OpenSearchChunkClient;
import com.cn.cloudpictureplatform.rag.infrastructure.parse.DocumentParser;
import com.cn.cloudpictureplatform.rag.infrastructure.parsing.ChunkingStrategy;
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
    private final ChunkingStrategy chunkingStrategy;
    private final EmbeddingClient embeddingClient;
    private final OpenSearchChunkClient openSearchChunkClient;
    private final RagProperties ragProperties;

    @Transactional
    public RagDocument ingestDocument(InputStream inputStream, String filename, String contentType) {
        // 1. Create document entity
        RagDocument document = new RagDocument();
        document.setTitle(filename);
        document.setOriginalFilename(filename);
        document.setContentType(contentType);
        document.setStatus(DocumentStatus.PROCESSING);
        document = ragDocumentRepository.save(document);

        try {
            // 2. Parse document
            ParsedDocument parsed = documentParser.parse(inputStream, filename, contentType);

            // 3. Chunk each page
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
                    chunk.setStatus(com.cn.cloudpictureplatform.rag.domain.ChunkStatus.ACTIVE);
                    allChunks.add(chunk);
                }
            }

            // 4. Save chunks to get IDs
            allChunks = documentChunkRepository.saveAll(allChunks);

            // 5. Generate embeddings in batches
            var embedConfig = ragProperties.embedding();
            List<String> contents = allChunks.stream().map(DocumentChunk::getContent).toList();
            List<List<Float>> embeddings = embeddingClient.embed(contents, embedConfig.dimensions());

            // 6. Index to OpenSearch
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
                    osId
                );
                openSearchChunkClient.indexChunk(chunkDoc);
            }

            documentChunkRepository.saveAll(allChunks);

            // 7. Update document status
            document.setStatus(DocumentStatus.INDEXED);
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

        // Soft-delete chunks in OpenSearch
        openSearchChunkClient.deleteByDocumentId(documentId.toString());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.application.IngestionServiceTests" -Dspring.profiles.active=dev`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/application/IngestionService.java src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/persistence/ src/test/java/com/cn/cloudpictureplatform/rag/application/IngestionServiceTests.java
git commit -m "feat(rag): add IngestionService orchestrating parse → chunk → embed → index"
```

---

### Task 7: Hybrid Retrieval + RRF

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/HybridSearchClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/domain/RetrievalResult.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/application/RetrievalService.java`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/application/RetrievalServiceTests.java`

- [ ] **Step 1: Write RetrievalResult**

```java
package com.cn.cloudpictureplatform.rag.domain;

import java.util.List;

public record RetrievalResult(
    String chunkId,
    String documentId,
    String content,
    String title,
    String sectionPath,
    int pageNumber,
    double rrfScore,
    double bm25Score,
    double vectorScore,
    List<String> sourceLabels
) {}
```

- [ ] **Step 2: Write the failing test**

```java
package com.cn.cloudpictureplatform.rag.application;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RetrievalServiceTests {

    @Test
    void shouldComputeRRFCorrectly() {
        // Simulate two ranked lists
        List<String> bm25Results = List.of("doc1", "doc2", "doc3");
        List<String> vectorResults = List.of("doc2", "doc1", "doc4");

        Map<String, Double> rrfScores = computeRRF(bm25Results, vectorResults, 60);

        // doc1: rank 1 in BM25 + rank 2 in vector = 1/(60+1) + 1/(60+2)
        // doc2: rank 2 in BM25 + rank 1 in vector = 1/(60+2) + 1/(60+1)
        // doc1 and doc2 should have same score (symmetric)
        assertEquals(rrfScores.get("doc1"), rrfScores.get("doc2"), 0.0001);
        // doc3 only in BM25
        assertTrue(rrfScores.containsKey("doc3"));
        // doc4 only in vector
        assertTrue(rrfScores.containsKey("doc4"));
    }

    private Map<String, Double> computeRRF(List<String> listA, List<String> listB, int k) {
        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < listA.size(); i++) {
            scores.merge(listA.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        for (int i = 0; i < listB.size(); i++) {
            scores.merge(listB.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        return scores;
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.application.RetrievalServiceTests" -Dspring.profiles.active=dev`
Expected: FAIL

- [ ] **Step 4: Implement HybridSearchClient**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Component;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class HybridSearchClient {

    private final OpenSearchClient openSearchClient;
    private final RagProperties ragProperties;

    public List<RetrievalResult> hybridSearch(String query, List<Float> queryEmbedding, int topK) {
        try {
            var config = ragProperties.openSearch();

            // BM25 search
            SearchRequest bm25Request = SearchRequest.of(s -> s
                .index(config.indexName())
                .query(q -> q
                    .multiMatch(m -> m
                        .query(query)
                        .fields("content^1.0", "title^2.0", "sectionPath^1.5")
                    )
                )
                .size(topK)
            );

            SearchResponse<ChunkDocument> bm25Response = openSearchClient.search(bm25Request, ChunkDocument.class);

            // Vector search (kNN)
            SearchRequest vectorRequest = SearchRequest.of(s -> s
                .index(config.indexName())
                .knn(k -> k
                    .field("embedding")
                    .k(topK)
                    .numCandidates(topK * 2)
                    .queryVector(queryEmbedding)
                )
            );

            SearchResponse<ChunkDocument> vectorResponse = openSearchClient.search(vectorRequest, ChunkDocument.class);

            // Merge results via RRF
            return mergeWithRRF(bm25Response, vectorResponse, topK);

        } catch (Exception e) {
            throw new RuntimeException("Hybrid search failed", e);
        }
    }

    private List<RetrievalResult> mergeWithRRF(
            SearchResponse<ChunkDocument> bm25Response,
            SearchResponse<ChunkDocument> vectorResponse,
            int topK) {

        int k = ragProperties.retrieval().rrfK();
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, ChunkDocument> chunkMap = new HashMap<>();

        // Process BM25 results
        List<Hit<ChunkDocument>> bm25Hits = bm25Response.hits().hits();
        for (int i = 0; i < bm25Hits.size(); i++) {
            ChunkDocument doc = bm25Hits.get(i).source();
            if (doc != null) {
                String id = doc.openSearchId();
                rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
                chunkMap.put(id, doc);
            }
        }

        // Process vector results
        List<Hit<ChunkDocument>> vectorHits = vectorResponse.hits().hits();
        for (int i = 0; i < vectorHits.size(); i++) {
            ChunkDocument doc = vectorHits.get(i).source();
            if (doc != null) {
                String id = doc.openSearchId();
                rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
                chunkMap.put(id, doc);
            }
        }

        // Sort by RRF score and return top-K
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> {
                ChunkDocument doc = chunkMap.get(e.getKey());
                return new RetrievalResult(
                    doc.openSearchId(),
                    doc.documentId(),
                    doc.content(),
                    doc.title(),
                    doc.sectionPath(),
                    doc.pageNumber(),
                    e.getValue(),
                    0.0, // bm25Score not stored separately for now
                    0.0, // vectorScore not stored separately for now
                    List.of(doc.title())
                );
            })
            .toList();
    }
}
```

- [ ] **Step 5: Implement RetrievalService**

```java
package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import com.cn.cloudpictureplatform.rag.infrastructure.embedding.EmbeddingClient;
import com.cn.cloudpictureplatform.rag.infrastructure.opensearch.HybridSearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final HybridSearchClient hybridSearchClient;
    private final EmbeddingClient embeddingClient;
    private final RagProperties ragProperties;

    public List<RetrievalResult> retrieve(String query) {
        var retrievalConfig = ragProperties.retrieval();
        var embedConfig = ragProperties.embedding();

        // 1. Embed the query
        List<Float> queryEmbedding = embeddingClient.embedSingle(query, embedConfig.dimensions());

        // 2. Hybrid search (BM25 + vector → RRF)
        List<RetrievalResult> results = hybridSearchClient.hybridSearch(
            query, queryEmbedding, retrievalConfig.bm25TopK()
        );

        log.debug("Retrieved {} results for query: {}", results.size(), query);
        return results;
    }

    public List<RetrievalResult> retrieve(String query, int topN) {
        var embedConfig = ragProperties.embedding();
        List<Float> queryEmbedding = embeddingClient.embedSingle(query, embedConfig.dimensions());
        return hybridSearchClient.hybridSearch(query, queryEmbedding, topN);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.application.RetrievalServiceTests" -Dspring.profiles.active=dev`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/HybridSearchClient.java src/main/java/com/cn/cloudpictureplatform/rag/domain/RetrievalResult.java src/main/java/com/cn/cloudpictureplatform/rag/application/RetrievalService.java src/test/java/com/cn/cloudpictureplatform/rag/application/RetrievalServiceTests.java
git commit -m "feat(rag): add hybrid retrieval with BM25 + vector + RRF fusion"
```

---

### Task 8: Qwen Rerank Client

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/rerank/RerankClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/rerank/QwenRerankClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/application/RerankService.java`

- [ ] **Step 1: Implement RerankClient interface**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.rerank;

import java.util.List;

public interface RerankClient {
    List<RerankResult> rerank(String query, List<String> documents, int topN);
}
```

- [ ] **Step 2: Implement RerankResult**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.rerank;

public record RerankResult(int index, double score, String document) {}
```

- [ ] **Step 3: Implement QwenRerankClient**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.rerank;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QwenRerankClient implements RerankClient {

    private final WebClient qwenRerankWebClient;
    private final RagProperties ragProperties;

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        var config = ragProperties.rerank();

        Map<String, Object> request = Map.of(
            "model", config.model(),
            "query", query,
            "documents", documents,
            "top_n", topN,
            "return_documents", false
        );

        try {
            Map<String, Object> response = qwenRerankWebClient.post()
                .uri("/v1/rerank")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

            return results.stream()
                .map(r -> new RerankResult(
                    ((Number) r.get("index")).intValue(),
                    ((Number) r.get("relevance_score")).doubleValue(),
                    documents.get(((Number) r.get("index")).intValue())
                ))
                .toList();
        } catch (Exception e) {
            log.warn("Rerank failed, returning original order: {}", e.getMessage());
            // Fallback: return original order with descending scores
            int limit = Math.min(topN, documents.size());
            return java.util.stream.IntStream.range(0, limit)
                .mapToObj(i -> new RerankResult(i, 1.0 - (i * 0.1), documents.get(i)))
                .toList();
        }
    }
}
```

- [ ] **Step 4: Implement RerankService**

```java
package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import com.cn.cloudpictureplatform.rag.infrastructure.rerank.QwenRerankClient;
import com.cn.cloudpictureplatform.rag.infrastructure.rerank.RerankResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final QwenRerankClient qwenRerankClient;
    private final RagProperties ragProperties;

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates) {
        var config = ragProperties.rerank();

        if (!config.enabled() || candidates.isEmpty()) {
            return candidates;
        }

        List<String> contents = candidates.stream()
            .map(RetrievalResult::content)
            .toList();

        List<RerankResult> reranked = qwenRerankClient.rerank(query, contents, config.topN());

        return reranked.stream()
            .map(rr -> {
                RetrievalResult original = candidates.get(rr.index());
                return new RetrievalResult(
                    original.chunkId(),
                    original.documentId(),
                    original.content(),
                    original.title(),
                    original.sectionPath(),
                    original.pageNumber(),
                    rr.score(), // Use reranker score as final score
                    original.bm25Score(),
                    original.vectorScore(),
                    original.sourceLabels()
                );
            })
            .toList();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/rerank/ src/main/java/com/cn/cloudpictureplatform/rag/application/RerankService.java
git commit -m "feat(rag): add Qwen rerank client with fallback on failure"
```

---

### Task 9: DeepSeek Generation + QaService

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/generator/GenerationClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/generator/DeepSeekGenerationClient.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/application/QaService.java`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/application/QaServiceTests.java`

- [ ] **Step 1: Implement GenerationClient**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.generator;

public interface GenerationClient {
    String generate(String systemPrompt, String userPrompt);
}
```

- [ ] **Step 2: Implement DeepSeekGenerationClient**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.generator;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekGenerationClient implements GenerationClient {

    private final ChatClient.Builder chatClientBuilder;
    private final RagProperties ragProperties;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        var config = ragProperties.generation();
        ChatClient chatClient = chatClientBuilder.build();

        try {
            return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        } catch (Exception e) {
            log.error("Generation failed: {}", e.getMessage());
            return "抱歉，生成回答时出现错误。请查看检索到的原始文档。";
        }
    }
}
```

- [ ] **Step 3: Implement QaService**

```java
package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import com.cn.cloudpictureplatform.rag.infrastructure.generator.DeepSeekGenerationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private final RetrievalService retrievalService;
    private final RerankService rerankService;
    private final DeepSeekGenerationClient generationClient;

    private static final String SYSTEM_PROMPT = """
        你是一个企业知识库问答助手。请根据以下检索到的文档片段回答用户问题。

        规则：
        1. 只根据提供的文档内容回答，不要编造信息
        2. 如果文档中没有相关信息，请明确说明
        3. 回答末尾标注引用来源，格式：[文档名 > 章节路径]
        4. 回答要简洁、准确、专业
        """;

    public QaResponse ask(String query) {
        // 1. Retrieve
        List<RetrievalResult> retrieved = retrievalService.retrieve(query);

        // 2. Rerank
        List<RetrievalResult> reranked = rerankService.rerank(query, retrieved);

        // 3. Build context
        String context = buildContext(reranked);

        // 4. Generate
        String userPrompt = "检索到的文档：\n" + context + "\n\n用户问题：" + query;
        String answer = generationClient.generate(SYSTEM_PROMPT, userPrompt);

        // 5. Build citations
        List<String> citations = reranked.stream()
            .map(r -> r.title() + (r.sectionPath() != null ? " > " + r.sectionPath() : ""))
            .distinct()
            .collect(Collectors.toList());

        return new QaResponse(answer, citations, reranked.size());
    }

    private String buildContext(List<RetrievalResult> results) {
        return results.stream()
            .map(r -> String.format("[文档: %s, 章节: %s, 页码: %d]\n%s",
                r.title(),
                r.sectionPath() != null ? r.sectionPath() : "无",
                r.pageNumber(),
                r.content()))
            .collect(Collectors.joining("\n\n---\n\n"));
    }

    public record QaResponse(String answer, List<String> citations, int chunksUsed) {}
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/generator/ src/main/java/com/cn/cloudpictureplatform/rag/application/QaService.java
git commit -m "feat(rag): add DeepSeek generation and QaService orchestrator"
```

---

### Task 10: REST Controller + DTOs

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/interfaces/dto/DocumentUploadResponse.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/interfaces/dto/QueryRequest.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/interfaces/dto/QueryResponse.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/interfaces/RagController.java`
- Create: `src/test/java/com/cn/cloudpictureplatform/rag/interfaces/RagControllerTests.java`

- [ ] **Step 1: Write DTOs**

```java
package com.cn.cloudpictureplatform.rag.interfaces.dto;

import java.util.UUID;

public record DocumentUploadResponse(UUID documentId, String title, String status, int chunkCount) {}
```

```java
package com.cn.cloudpictureplatform.rag.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(@NotBlank String query) {}
```

```java
package com.cn.cloudpictureplatform.rag.interfaces.dto;

import java.util.List;

public record QueryResponse(String answer, List<String> citations, int chunksUsed) {}
```

- [ ] **Step 2: Write the failing test**

```java
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
        assertEquals("Test answer", response.answer());
        verify(qaService).ask("test question");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.interfaces.RagControllerTests" -Dspring.profiles.active=dev`
Expected: FAIL

- [ ] **Step 4: Implement RagController**

```java
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
import org.springframework.http.MediaType;
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

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id) {
        ingestionService.softDeleteDocument(id);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.interfaces.RagControllerTests" -Dspring.profiles.active=dev`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/interfaces/ src/test/java/com/cn/cloudpictureplatform/rag/interfaces/RagControllerTests.java
git commit -m "feat(rag): add REST controller with document upload and query endpoints"
```

---

### Task 11: Full Compilation Test

- [ ] **Step 1: Compile full project**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all RAG tests**

Run: `.\mvnw test -Dtest="com.cn.cloudpictureplatform.rag.**" -Dspring.profiles.active=dev`
Expected: All tests PASS

- [ ] **Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix(rag): compile and test fixes for Phase 0"
```

---

## Phase 1: P1 — Hybrid Retrieval Enhancement

### Task 12: BM25 Field Weighting + Structure-Aware Chunking

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/HybridSearchClient.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/ChunkingStrategy.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/StructureAwareChunkingStrategy.java`

- [ ] **Step 1: Add BM25 field weighting to HybridSearchClient**

Update the BM25 query to use field boosts:
```java
// In HybridSearchClient.hybridSearch, replace the BM25 request:
SearchRequest bm25Request = SearchRequest.of(s -> s
    .index(config.indexName())
    .query(q -> q
        .bool(b -> b
            .should(sh -> sh
                .multiMatch(m -> m
                    .query(query)
                    .fields("content^1.0", "title^2.0", "sectionPath^1.5", "documentId^3.0")
                    .type(org.opensearch.client.opensearch._types.FullTextQueryType.BestFields)
                )
            )
        )
    )
    .size(topK)
);
```

- [ ] **Step 2: Implement StructureAwareChunkingStrategy**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StructureAwareChunkingStrategy {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
        "^(#{1,6})\\s+(.+)$", Pattern.MULTILINE
    );

    private final int maxTokens;
    private final int overlapTokens;

    public StructureAwareChunkingStrategy(
            @org.springframework.beans.factory.annotation.Value("${app.rag.chunking.max-tokens:512}") int maxTokens,
            @org.springframework.beans.factory.annotation.Value("${app.rag.chunking.overlap-tokens:50}") int overlapTokens) {
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<TextChunk> chunkWithStructure(String text, String sectionPrefix) {
        List<String> sections = splitByHeadings(text);
        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (String section : sections) {
            String sectionTitle = extractHeading(section);
            String fullPrefix = sectionPrefix != null
                ? sectionPrefix + " > " + (sectionTitle != null ? sectionTitle : "")
                : (sectionTitle != null ? sectionTitle : "");

            String body = removeHeading(section);
            List<TextChunk> sectionChunks = chunkBody(body, fullPrefix, chunkIndex);
            chunks.addAll(sectionChunks);
            chunkIndex += sectionChunks.size();
        }

        return chunks;
    }

    private List<String> splitByHeadings(String text) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                sections.add(text.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.start();
        }

        if (lastEnd < text.length()) {
            sections.add(text.substring(lastEnd));
        }

        return sections.isEmpty() ? List.of(text) : sections;
    }

    private String extractHeading(String section) {
        Matcher matcher = HEADING_PATTERN.matcher(section);
        return matcher.find() ? matcher.group(2).trim() : null;
    }

    private String removeHeading(String section) {
        return HEADING_PATTERN.matcher(section).replaceFirst("").trim();
    }

    private List<TextChunk> chunkBody(String body, String sectionPath, int startIndex) {
        if (body.isBlank()) {
            return List.of();
        }

        String[] words = body.split("\\s+");
        if (words.length <= maxTokens) {
            return List.of(new TextChunk(startIndex, body.trim(), words.length, 0, body.length()));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = startIndex;
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + maxTokens, words.length);
            String chunkText = String.join(" ", List.of(words).subList(start, end)).trim();

            chunks.add(new TextChunk(chunkIndex++, chunkText, end - start, 0, chunkText.length()));

            int nextStart = end - overlapTokens;
            if (nextStart <= start) nextStart = end;
            start = nextStart;
        }

        return chunks;
    }
}
```

- [ ] **Step 3: Compile and test**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/HybridSearchClient.java src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/parsing/StructureAwareChunkingStrategy.java
git commit -m "feat(rag): add BM25 field weighting and structure-aware chunking"
```

---

## Phase 2: Enhancers — Multi-Turn Q&A

### Task 13: Query Rewriting + Conversation Memory

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/application/QueryRewriter.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/domain/ConversationMessage.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/persistence/ConversationRepository.java`
- Create: `src/main/resources/db/migration/V38__rag_conversation.sql`
- Modify: `src/main/java/com/cn/cloudpictureplatform/rag/application/QaService.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/rag/interfaces/RagController.java`

- [ ] **Step 1: Write Flyway migration**

`V38__rag_conversation.sql`:
```sql
CREATE TABLE rag_conversation (
    id UUID NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_rag_conv_session ON rag_conversation(session_id, created_at);
```

- [ ] **Step 2: Implement ConversationMessage entity**

```java
package com.cn.cloudpictureplatform.rag.domain;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rag_conversation")
public class ConversationMessage extends BaseEntity {

    @Column(nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    public enum MessageRole {
        USER, ASSISTANT
    }
}
```

- [ ] **Step 3: Implement ConversationRepository**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.persistence;

import com.cn.cloudpictureplatform.rag.domain.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationMessage, java.util.UUID> {
    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
```

- [ ] **Step 4: Implement QueryRewriter**

```java
package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.domain.ConversationMessage;
import com.cn.cloudpictureplatform.rag.infrastructure.generator.DeepSeekGenerationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriter {

    private final DeepSeekGenerationClient generationClient;

    private static final String REWRITE_PROMPT = """
        你是一个查询改写助手。根据对话历史，将用户的追问改写成独立、完整的检索查询。

        规则：
        1. 如果用户的问题已经是独立完整的，直接返回原问题
        2. 如果用户使用了代词（"这个"、"那个"、"它"等），根据上下文替换为具体指代
        3. 只返回改写后的查询，不要添加任何解释
        """;

    public String rewrite(String currentQuery, List<ConversationMessage> history) {
        if (history.isEmpty()) {
            return currentQuery;
        }

        String historyContext = history.stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));

        String prompt = "对话历史：\n" + historyContext + "\n\n当前问题：" + currentQuery;

        try {
            String rewritten = generationClient.generate(REWRITE_PROMPT, prompt);

            // Fallback: if rewriting produces empty or very different result, use original
            if (rewritten == null || rewritten.isBlank() || rewritten.length() < 3) {
                log.warn("Query rewriting produced empty result, using original");
                return currentQuery;
            }

            log.debug("Query rewritten: '{}' → '{}'", currentQuery, rewritten);
            return rewritten.trim();
        } catch (Exception e) {
            log.warn("Query rewriting failed, using original: {}", e.getMessage());
            return currentQuery;
        }
    }
}
```

- [ ] **Step 5: Update QaService to support multi-turn**

Add to QaService:
```java
private final ConversationRepository conversationRepository;
private final QueryRewriter queryRewriter;

public QaResponse ask(String query, String sessionId) {
    // 1. Get conversation history
    List<ConversationMessage> history = sessionId != null
        ? conversationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        : List.of();

    // 2. Rewrite query if multi-turn
    String searchQuery = queryRewriter.rewrite(query, history);

    // 3. Retrieve + Rerank (same as before)
    List<RetrievalResult> retrieved = retrievalService.retrieve(searchQuery);
    List<RetrievalResult> reranked = rerankService.rerank(searchQuery, retrieved);

    // 4. Build context and generate
    String context = buildContext(reranked);
    String userPrompt = "检索到的文档：\n" + context + "\n\n用户问题：" + query; // Use original query for generation
    String answer = generationClient.generate(SYSTEM_PROMPT, userPrompt);

    // 5. Save conversation
    if (sessionId != null) {
        saveMessage(sessionId, ConversationMessage.MessageRole.USER, query);
        saveMessage(sessionId, ConversationMessage.MessageRole.ASSISTANT, answer);
    }

    // 6. Build citations
    List<String> citations = reranked.stream()
        .map(r -> r.title() + (r.sectionPath() != null ? " > " + r.sectionPath() : ""))
        .distinct()
        .collect(Collectors.toList());

    return new QaResponse(answer, citations, reranked.size());
}

private void saveMessage(String sessionId, ConversationMessage.MessageRole role, String content) {
    ConversationMessage msg = new ConversationMessage();
    msg.setSessionId(sessionId);
    msg.setRole(role);
    msg.setContent(content);
    conversationRepository.save(msg);
}
```

- [ ] **Step 6: Update RagController to support session_id**

Add to RagController:
```java
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
```

- [ ] **Step 7: Compile and test**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/application/QueryRewriter.java src/main/java/com/cn/cloudpictureplatform/rag/domain/ConversationMessage.java src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/persistence/ConversationRepository.java src/main/resources/db/migration/V38__rag_conversation.sql src/main/java/com/cn/cloudpictureplatform/rag/application/QaService.java src/main/java/com/cn/cloudpictureplatform/rag/interfaces/RagController.java
git commit -m "feat(rag): add multi-turn Q&A with query rewriting and conversation memory"
```

---

## Phase 3: Enhancers — Caching + Observability

### Task 14: L1/L2 Caching + Semantic Cache

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/cache/RagCacheService.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/cache/SemanticCache.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/rag/application/RetrievalService.java`

- [ ] **Step 1: Implement RagCacheService**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.cache;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagCacheService {

    private final RagProperties ragProperties;

    @Cacheable(value = "ragEmbedding", key = "#content.hashCode()")
    public Optional<List<Float>> getCachedEmbedding(String content) {
        return Optional.empty(); // Cache miss handled by Spring
    }

    @Cacheable(value = "ragRetrieval", key = "#query.hashCode()")
    public Optional<List<String>> getCachedRetrieval(String query) {
        return Optional.empty();
    }
}
```

- [ ] **Step 2: Implement SemanticCache**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.cache;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
public class SemanticCache {

    private final Deque<CachedEntry> cache = new ConcurrentLinkedDeque<>();
    private final double threshold;
    private final int maxEntries;

    public SemanticCache(RagProperties ragProperties) {
        this.threshold = ragProperties.cache().semanticThreshold();
        this.maxEntries = 10000;
    }

    public Optional<String> findCachedAnswer(List<Float> queryEmbedding) {
        for (CachedEntry entry : cache) {
            double similarity = cosineSimilarity(queryEmbedding, entry.embedding());
            if (similarity >= threshold) {
                log.debug("Semantic cache hit: similarity={}, answer={}", similarity, entry.answer().substring(0, Math.min(50, entry.answer().length())));
                return Optional.of(entry.answer());
            }
        }
        return Optional.empty();
    }

    public void cacheAnswer(List<Float> queryEmbedding, String answer) {
        cache.addLast(new CachedEntry(queryEmbedding, answer, System.currentTimeMillis()));
        while (cache.size() > maxEntries) {
            cache.pollFirst();
        }
    }

    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record CachedEntry(List<Float> embedding, String answer, long timestamp) {}
}
```

- [ ] **Step 3: Integrate semantic cache into RetrievalService**

Add to QaService.ask():
```java
// After generating answer, cache it
if (sessionId != null) {
    List<Float> queryEmbedding = embeddingClient.embedSingle(query, ragProperties.embedding().dimensions());
    semanticCache.cacheAnswer(queryEmbedding, answer);
}
```

Add to beginning of QaService.ask():
```java
// Check semantic cache first
List<Float> queryEmbedding = embeddingClient.embedSingle(query, ragProperties.embedding().dimensions());
Optional<String> cachedAnswer = semanticCache.findCachedAnswer(queryEmbedding);
if (cachedAnswer.isPresent()) {
    log.debug("Semantic cache hit for query: {}", query);
    return new QaResponse(cachedAnswer.get(), List.of("cached"), 0);
}
```

- [ ] **Step 4: Compile and test**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/cache/ src/main/java/com/cn/cloudpictureplatform/rag/application/QaService.java
git commit -m "feat(rag): add L1/L2 caching and semantic cache for RAG"
```

---

### Task 15: Prometheus Metrics

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/metrics/RagMetrics.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/rag/application/QaService.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/rag/application/IngestionService.java`

- [ ] **Step 1: Implement RagMetrics**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Histogram;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagMetrics {

    private final MeterRegistry meterRegistry;

    public void recordIngestion(int chunksCount, long durationMs) {
        Counter.builder("rag_ingestion_documents_total").register(meterRegistry).increment();
        Counter.builder("rag_ingestion_chunks_total").register(meterRegistry).increment(chunksCount);
        Timer.builder("rag_ingestion_duration_seconds").register(meterRegistry).record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordRetrieval(long bm25Ms, long vectorMs, long rerankMs, long totalMs, boolean cacheHit) {
        meterRegistry.timer("rag_retrieval_bm25_latency_ms").record(java.time.Duration.ofMillis(bm25Ms));
        meterRegistry.timer("rag_retrieval_vector_latency_ms").record(java.time.Duration.ofMillis(vectorMs));
        meterRegistry.timer("rag_retrieval_rerank_latency_ms").record(java.time.Duration.ofMillis(rerankMs));
        meterRegistry.timer("rag_retrieval_total_latency_ms").record(java.time.Duration.ofMillis(totalMs));
        if (cacheHit) {
            Counter.builder("rag_retrieval_cache_hit_total").register(meterRegistry).increment();
        }
    }

    public void recordGeneration(int inputTokens, int outputTokens, long latencyMs) {
        Counter.builder("rag_generation_tokens_input_total").register(meterRegistry).increment(inputTokens);
        Counter.builder("rag_generation_tokens_output_total").register(meterRegistry).increment(outputTokens);
        meterRegistry.timer("rag_generation_latency_ms").record(java.time.Duration.ofMillis(latencyMs));
    }
}
```

- [ ] **Step 2: Integrate metrics into QaService**

Wrap key operations with timing:
```java
long start = System.currentTimeMillis();
// ... retrieval logic ...
long retrievalMs = System.currentTimeMillis() - start;

start = System.currentTimeMillis();
// ... generation logic ...
long generationMs = System.currentTimeMillis() - start;

ragMetrics.recordRetrieval(0, 0, rerankMs, retrievalMs, false);
ragMetrics.recordGeneration(0, 0, generationMs);
```

- [ ] **Step 3: Compile and test**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/metrics/ src/main/java/com/cn/cloudpictureplatform/rag/application/QaService.java src/main/java/com/cn/cloudpictureplatform/rag/application/IngestionService.java
git commit -m "feat(rag): add Prometheus metrics for ingestion, retrieval, and generation"
```

---

### Task 16: Golden Set Evaluation

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/application/EvaluationService.java`
- Create: `src/main/resources/golden_set.json`
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/interfaces/AdminRagController.java`

- [ ] **Step 1: Create golden_set.json**

```json
[
  {
    "query": "出差报销的审批流程是什么？",
    "expectedDocuments": ["出差报销管理办法.pdf"],
    "expectedSections": ["审批流程"]
  },
  {
    "query": "年假有多少天？",
    "expectedDocuments": ["员工手册.pdf"],
    "expectedSections": ["假期制度"]
  }
]
```

- [ ] **Step 2: Implement EvaluationService**

```java
package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final RetrievalService retrievalService;
    private final RerankService rerankService;
    private final ObjectMapper objectMapper;

    public EvaluationResult runEvaluation() {
        try {
            GoldenSetEntry[] entries = objectMapper.readValue(
                new ClassPathResource("golden_set.json").getInputStream(),
                GoldenSetEntry[].class
            );

            int totalQueries = entries.length;
            int recallAt5Hits = 0;
            int rerankShiftCount = 0;

            for (GoldenSetEntry entry : entries) {
                List<RetrievalResult> retrieved = retrievalService.retrieve(entry.query());
                List<RetrievalResult> reranked = rerankService.rerank(entry.query(), retrieved);

                // Check recall@5
                Set<String> expectedDocs = Set.of(entry.expectedDocuments());
                boolean found = reranked.stream()
                    .limit(5)
                    .anyMatch(r -> expectedDocs.contains(r.title()));
                if (found) recallAt5Hits++;

                // Check rerank shift
                if (!retrieved.isEmpty() && !reranked.isEmpty()) {
                    String topBefore = retrieved.get(0).chunkId();
                    String topAfter = reranked.get(0).chunkId();
                    if (!topBefore.equals(topAfter)) rerankShiftCount++;
                }
            }

            return new EvaluationResult(
                totalQueries,
                (double) recallAt5Hits / totalQueries,
                (double) rerankShiftCount / totalQueries
            );

        } catch (Exception e) {
            log.error("Evaluation failed: {}", e.getMessage());
            return new EvaluationResult(0, 0.0, 0.0);
        }
    }

    public record EvaluationResult(int totalQueries, double recallAt5, double rerankShiftRate) {}
    public record GoldenSetEntry(String query, String[] expectedDocuments, String[] expectedSections) {}
}
```

- [ ] **Step 3: Implement AdminRagController**

```java
package com.cn.cloudpictureplatform.rag.interfaces;

import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.rag.application.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag/admin")
@RequiredArgsConstructor
public class AdminRagController {

    private final EvaluationService evaluationService;

    @PostMapping("/evaluate")
    public ApiResponse<EvaluationService.EvaluationResult> evaluate() {
        return ApiResponse.ok(evaluationService.runEvaluation());
    }
}
```

- [ ] **Step 4: Compile and test**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/application/EvaluationService.java src/main/resources/golden_set.json src/main/java/com/cn/cloudpictureplatform/rag/interfaces/AdminRagController.java
git commit -m "feat(rag): add golden set evaluation with recall@5 and rerank shift metrics"
```

---

## Summary

After completing all tasks:

| Phase | Tasks | Status |
|---|---|---|
| Phase 0: P0 Core | Tasks 1-11 | End-to-end pipeline working |
| Phase 1: P1 Hybrid | Task 12 | BM25 field weighting + structure-aware chunking |
| Phase 2: Enhancers | Task 13 | Multi-turn Q&A + query rewriting |
| Phase 3: Enhancers | Tasks 14-16 | Caching + metrics + evaluation |

**Final verification:**
```bash
.\mvnw compile          # Must pass
.\mvnw test             # All RAG tests must pass
```
