# Design: OpenSearch Chunk Index + Client

## Overview
Implement OpenSearch integration for RAG chunk storage, including a document record, client for indexing/deleting, and index initializer.

## Components

### ChunkDocument
- Java record representing a chunk to be indexed in OpenSearch.
- Fields: documentId, chunkIndex, content, tokenCount, title, sectionPath, pageNumber, bm25Boost, embeddingDim, openSearchId.

### OpenSearchChunkClient
- Spring component injected with `OpenSearchClient` and `RagProperties`.
- Methods:
  - `indexChunk(ChunkDocument)`: Indexes a chunk document into OpenSearch index.
  - `deleteByDocumentId(String)`: Deletes all chunks for a given document ID.

### OpenSearchIndexInitializer
- Listens for `ApplicationReadyEvent` to create OpenSearch index if not exists.
- Defines mappings for fields with appropriate types (keyword, text, integer, float).
- Uses standard analyzer for text fields.

## Dependencies
- Uses existing `OpenSearchClient` bean from `RagConfig`.
- Uses `RagProperties.OpenSearch` for index name and base URL.

## Testing
- Unit test `OpenSearchChunkClientTests` verifies ChunkDocument creation.

## Files to Create
1. `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/ChunkDocument.java`
2. `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchChunkClient.java`
3. `src/main/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchIndexInitializer.java`
4. `src/test/java/com/cn/cloudpictureplatform/rag/infrastructure/opensearch/OpenSearchChunkClientTests.java`

## Implementation Steps
1. Write failing test for ChunkDocument.
2. Implement ChunkDocument record.
3. Implement OpenSearchChunkClient.
4. Implement OpenSearchIndexInitializer.
5. Run tests to verify they pass.
6. Commit changes.

## Design Approved
User approved the exact spec provided in task description.