# OpenSearch Chunk Index + Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement OpenSearch chunk storage with document record, client, and index initializer for RAG system.

**Architecture:** Create three classes in `com.cn.cloudpictureplatform.rag.infrastructure.opensearch` package: ChunkDocument (record), OpenSearchChunkClient (Spring component), OpenSearchIndexInitializer (event listener). Use existing OpenSearchClient bean from RagConfig.

**Tech Stack:** Java 21, Spring Boot 4.0.5, OpenSearch Java Client 2.x, Lombok, JUnit 5.

---

### Task 1: Write failing test for ChunkDocument

**Files:**
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
Expected: FAIL (class not found)

### Task 2: Implement ChunkDocument record

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/ChunkDocument.java`

- [ ] **Step 3: Write minimal implementation**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

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

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.opensearch.OpenSearchChunkClientTests" -Dspring.profiles.active=dev`
Expected: PASS

### Task 3: Implement OpenSearchChunkClient

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchChunkClient.java`

- [ ] **Step 5: Write implementation**

```java
package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
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

- [ ] **Step 6: Run tests to verify they pass**

Run: `.\mvnw test -pl . -Dtest="com.cn.cloudpictureplatform.rag.infrastructure.opensearch.*" -Dspring.profiles.active=dev`
Expected: PASS (including existing test)

### Task 4: Implement OpenSearchIndexInitializer

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchIndexInitializer.java`

- [ ] **Step 7: Write implementation**

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
                        .properties("content", p -> p.text(t -> t.analyzer("standard")))
                        .properties("tokenCount", p -> p.integer(i -> i))
                        .properties("title", p -> p.text(t -> t.analyzer("standard")))
                        .properties("sectionPath", p -> p.text(t -> t.analyzer("standard")))
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

- [ ] **Step 8: Run all tests to verify they pass**

Run: `.\mvnw test -pl . -Dspring.profiles.active=dev`
Expected: PASS

### Task 5: Commit changes

**Files:**
- Commit all created files.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/ src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchChunkClientTests.java
git commit -m "feat(rag): add OpenSearch chunk client and index initializer"
```