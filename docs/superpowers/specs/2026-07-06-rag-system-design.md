# RAG Enterprise Knowledge Base System — Design Spec

**Date:** 2026-07-06
**Project:** Extend Cloud Picture Platform
**Status:** Approved for implementation

## Overview

A Retrieval-Augmented Generation (RAG) system built as a new bounded context within Cloud Picture Platform. It ingests enterprise documents (policies, procedures, manuals), indexes them in OpenSearch with hybrid BM25+vector retrieval, and answers questions via DeepSeek with source citations.

**Interview goal:** Demonstrate deep understanding of RAG engineering — hybrid retrieval strategy, chunking design, reranking, multi-turn query rewriting, and evaluation methodology — rather than just calling an LLM API.

## Technology Stack

| Component | Choice | Rationale |
|---|---|---|
| Framework | Spring Boot 4.0.5 + Spring AI | Differentiates from Python/LangChain projects; demonstrates Java-native RAG |
| Ingestion | Spring AI DocumentReader + custom chunking | Reuse Spring AI's ETL for parsing; custom transformer for structure-aware splitting |
| Embedding | Qwen text-embedding-v3 (1024-dim) | Free via vLLM self-hosting; 1024-dim balances quality vs cost |
| Rerank | Qwen gte-rerank-v2 | Cross-encoder; same vendor as embedding = simpler integration |
| Vector Store | OpenSearch 2.12 | BM25 + kNN in one cluster; native hybrid search; no second system needed |
| Generation | DeepSeek-R1 / DeepSeek-V3 via ChatClient | Strong reasoning, low cost; used for generation only (not embedding) |
| Caching | Caffeine (L1) + Redis (L2) | Existing infrastructure; reuses FallbackCache pattern |
| Monitoring | Prometheus + Actuator | Existing infrastructure; add RAG-specific metrics |

**Key constraint:** DeepSeek does not provide an embedding API. Embedding is handled exclusively by Qwen. DeepSeek is used only for generation.

## Priority Matrix

Features are classified by interview criticality. If asked "what would you上线 first?", the answer is P0 only.

| Priority | Features | Interview Signal |
|---|---|---|
| **P0 — Core Path** | Ingestion (parse → chunk → embed → index), Vector similarity search, Generation (DeepSeek) | "I can build a working RAG system from scratch" |
| **P1 — Differentiators** | BM25 + hybrid retrieval, RRF fusion, Rerank (Qwen gte-rerank) | "I understand retrieval quality beyond basic vector search" |
| **Enhancers** | Multi-turn query rewriting, Semantic cache, Evaluation golden set, Knowledge freshness versioning | "I can design production-grade RAG with lifecycle management" |

**Interview rule:** P0 is the minimum viable demo. P1 is what makes you memorable. Enhancers are bonus depth if time permits.

## Fallback & Degradation Design

Every external vendor dependency has a graceful degradation path. This is a production engineering signal — not just "it works when everything is up."

| Dependency | Failure Mode | Degradation |
|---|---|---|
| Qwen Embedding API | Timeout / 5xx | Use cached embeddings if available; if cold start, queue ingestion and retry. Never block user query for embedding — read existing index only. |
| Qwen Rerank API | Timeout / 5xx | Skip rerank step; return RRF-fused results directly. Log degraded mode. User gets slightly lower quality but system stays responsive. |
| DeepSeek API | Timeout / 5xx | Return top-N retrieved chunks as raw context without generation. "I can't synthesize an answer, but here are the relevant documents." Better than error. |
| OpenSearch | Cluster down | Degrade to "ingestion paused, queries unavailable" with clear error message. Cache hits still work (Caffeine L1). |
| Redis | Unavailable | Silent fallback to Caffeine-only (existing FallbackCache pattern from Cloud Picture Platform). |

**Key principle:** The system should never return a 500 for a vendor timeout. Every external call has a fallback that degrades quality, not availability.

## Architecture

### Bounded Context Layout

```
com.cn.cloudpictureplatform/
  rag/
    domain/
      Document.java              — JPA entity: document metadata, version, status
      DocumentChunk.java         — JPA entity: chunk metadata (text ref, position, section)
      RetrievalResult.java       — value object: chunk + score + source info
    application/
      IngestionService.java      — orchestrates: parse → chunk → embed → index
      RetrievalService.java      — hybrid retrieval: BM25 + vector → RRF
      RerankService.java         — cross-encoder reranking via Qwen
      QaService.java             — multi-turn Q&A: rewrite → retrieve → rerank → generate
    infrastructure/
      opensearch/
        OpenSearchConfig.java    — client configuration
        HybridSearchClient.java  — BM25 + kNN parallel search + RRF fusion
        ChunkIndexMapper.java    — maps chunks to OpenSearch documents
      embedding/
        QwenEmbeddingClient.java — calls Qwen text-embedding-v3 API
      rerank/
        QwenRerankClient.java    — calls Qwen gte-rerank-v2 API
      generator/
        DeepSeekChatClient.java  — wraps Spring AI ChatClient for DeepSeek
    interfaces/
      dto/
        DocumentUploadRequest.java
        QueryRequest.java
        QueryResponse.java
        ChatRequest.java
        ChatResponse.java
      RagController.java         — REST endpoints
    config/
      RagProperties.java         — @ConfigurationProperties for RAG settings
      RagConfig.java             — bean definitions
```

### Shared Infrastructure

- **PostgreSQL** (existing): document metadata, chunk metadata, conversation history
- **OpenSearch** (new, Docker): chunk text + vectors + BM25 index
- **Redis + Caffeine** (existing): caching layer
- **Spring AI ChatClient** (existing pattern): LLM calls via AI gateway or direct DeepSeek API

## Design Details

### 1. Ingestion Pipeline

**Flow:**
```
Document upload (PDF/DOCX/TXT)
  → DocumentReader (Tika/PDFBox) extracts text + structure
  → StructureAnalyzer detects headings, paragraphs, tables
  → ChunkingStrategy splits with awareness of document structure
  → MetadataEnricher adds: docId, title, sectionPath, pageNumber, chunkIndex
  → EmbeddingService (Qwen text-embedding-v3) generates 1024-dim vectors
  → OpenSearchWriter indexes chunks (text + vector + metadata)
  → DocumentTracker stores document metadata in PostgreSQL
```

**Chunking Strategy:**

Phase 1 (MVP):
- Fixed-length splitting by token count (~512 tokens per chunk)
- 10% overlap between adjacent chunks to prevent context loss at boundaries
- Each chunk carries: `{docId, title, sectionPath, pageNumber, chunkIndex}`

Phase 2 (refined):
- Structure-aware: detect heading boundaries from PDF/DOCX styles
- Prefer splitting at paragraph boundaries
- Fall back to token count only when paragraph exceeds max length
- Preserve table integrity (never split mid-table)

Phase 3 (hierarchical — advanced differentiator):
- **Parent-child chunking (small-to-big retrieval):**
  - Parent chunk = full section (heading → next heading, up to 2000 tokens)
  - Child chunks = paragraphs within the section (~200-300 tokens each)
  - Retrieval: match against child chunks (precise) → return parent chunk as context (complete)
  - This solves the fundamental tension: small chunks retrieve precisely, but large chunks generate better answers
- **Interview value:** "I support hierarchical retrieval — small chunks for precision, parent context for completeness. This is the small-to-big retrieval pattern."
- Implementation: OpenSearch stores both child and parent chunk IDs; retrieval returns child matches but fetches parent text for the generator

**Document Versioning (Knowledge Freshness):**
- Re-ingesting the same document (updated version) performs a soft-delete of old chunks
- Old chunks marked as `superseded` with `superseded_by` pointing to new version
- New chunks ingested with incremented version number
- Old chunks retained until new ingestion completes (atomic swap)
- PostgreSQL tracks: `document.id, document.version, chunk.status (active/superseded/deleted)`

### 2. Hybrid Retrieval + RRF

**Stage 1: Parallel Retrieval**
```
Query → QueryPreprocessor (rewrite if multi-turn)
  → Parallel fan-out:
      BM25 search (OpenSearch full-text) → ranked list A (top-20)
      kNN vector search (OpenSearch dense) → ranked list B (top-20)
  → RRF Fusion:
      For each chunk in A ∪ B:
        rrf_score = Σ 1/(k + rank_i)  where k=60
  → Top-K candidates (K=20)
```

**Why RRF over weighted sum:**
- BM25 scores and cosine similarity are on incompatible scales
- RRF operates on rankings, not raw scores — no normalization needed
- k=60 is the standard constant from the original RRF paper (Cormack et al., 2009)

**Stage 2: Rerank**
```
Top-20 candidates → Qwen gte-rerank-v2 (cross-encoder)
  → Each (query, candidate) pair scored for relevance
  → Re-sort by reranker score
  → Top-N (N=5) fed to generator
```

**Final ranking: reranker scores only.** No mixing with RRF scores. The two-stage paradigm is: coarse recall (RRF) → precise ranking (rerank). Mixing re-introduces the scale incompatibility that RRF was designed to solve.

**BM25 Field Weighting:**
- Title field: boost 2.0x
- Section heading: boost 1.5x
- Body text: boost 1.0x (baseline)
- Document ID/code field: boost 3.0x (for exact product code lookups)

### 3. Multi-Turn Q&A

**Query Rewriting:**
```
User: "那这个政策的适用范围呢？"
  → Rewriter context: last_query + last_answer + last_retrieved_chunks (titles only)
  → Rewritten: "XX公司出差报销政策的适用范围是什么？"
  → Uses rewritten query for retrieval, original query for generation
```

**Dual-query fallback:** If rewriting fails or produces low-confidence result, fall back to original query directly. Never let rewriting be a single point of failure.

**Conversation Memory:**
- Stored in PostgreSQL (conversation_id → messages)
- Spring AI's `MessageChatMemoryAdvisor` integrated into ChatClient
- Retention: configurable TTL (default 30 days)

**Citation Generation:**
- Each chunk carries metadata: `{docTitle, sectionPath, pageNumber}`
- System prompt instructs: "Cite sources as [文档名 > 章节路径]"
- Post-processing validates that cited sources actually appear in retrieved chunks

### 4. Caching

| Cache Target | L1 (Caffeine) | L2 (Redis) | Key Format |
|---|---|---|---|
| Embedding vectors | 10min TTL | 24h TTL | `rag:emb:{sha256(content)}` |
| Retrieval results | 5min TTL | 1h TTL | `rag:ret:{sha256(query)}` |
| Rerank scores | 5min TTL | 1h TTL | `rag:rrk:{sha256(query+doc_ids)}` |

**Semantic Cache (advanced):**
- Cluster recent query embeddings in Caffeine
- On new query: compute cosine similarity against cached embeddings
- If similarity > threshold: return cached answer (skip retrieval + rerank + generation)
- **Threshold calibration:** 0.95 is the starting point, not the final value. Justification:
  - Below 0.90: too aggressive — returns answers to semantically different queries (precision loss)
  - 0.90-0.95: sweet spot for paraphrased queries ("怎么报销差旅费" ≈ "出差报销流程是什么")
  - Above 0.95: too conservative — only catches near-duplicates, misses paraphrases
  - **Tuning method:** offline evaluation against golden set; measure cache hit rate vs answer accuracy degradation. Can also use silhouette score on cached embedding clusters to find optimal threshold dynamically.
- Trade-off: slight accuracy loss for significant latency/cost reduction on paraphrased queries
- Implementation: store `(embedding, answer, timestamp)` tuples; linear scan is fast enough for <10K cached entries

### 5. Observability

**Prometheus Metrics:**
```
# Ingestion
rag_ingestion_documents_total          (counter)
rag_ingestion_chunks_total             (counter)
rag_ingestion_duration_seconds         (histogram)

# Retrieval
rag_retrieval_bm25_latency_ms          (histogram)
rag_retrieval_vector_latency_ms        (histogram)
rag_retrieval_rrf_latency_ms           (histogram)
rag_retrieval_rerank_latency_ms        (histogram)
rag_retrieval_total_latency_ms         (histogram)
rag_retrieval_cache_hit_total          (counter)

# Generation
rag_generation_tokens_input_total      (counter)
rag_generation_tokens_output_total     (counter)
rag_generation_latency_ms              (histogram)

# Q&A
rag_qa_query_total                     (counter)
rag_qa_rewritten_query_diff            (histogram) — cosine distance between original and rewritten
```

**Lightweight Evaluation:**
- `golden_set.json`: 20-30 test queries with expected source documents
- On-demand evaluation via `POST /api/rag/admin/evaluate`
- Metrics: recall@5, hit rate, rerank shift rate (% queries where reranker changes top-1)
- No RAGAS dependency — simple script comparing results against golden set

### 6. API Endpoints

```
POST   /api/rag/documents/upload          — upload document for ingestion
GET    /api/rag/documents                 — list ingested documents with status
DELETE /api/rag/documents/{id}            — soft-delete (triggers version lifecycle)
POST   /api/rag/documents/{id}/reingest  — re-ingest after document update

POST   /api/rag/query                    — single-turn Q&A
POST   /api/rag/chat                     — multi-turn Q&A (accepts session_id)
GET    /api/rag/chat/{session_id}/history — conversation history

GET    /api/rag/admin/stats              — ingestion stats, index health
POST   /api/rag/admin/evaluate           — run golden set evaluation
```

## Implementation Phases

### Phase 0: P0 — Core Path (3-5 days)
**Goal:** End-to-end pipeline that works, even if ugly.
- Tika/PDFBox document parsing
- Fixed-length chunking with overlap
- Qwen embedding (1024-dim) via HTTP client
- OpenSearch single-node (Docker): index chunks with vectors
- Basic kNN similarity search (no BM25 yet)
- DeepSeek generation via Spring AI ChatClient
- Single-turn Q&A working end-to-end
- Basic error handling: vendor timeout → graceful degradation

### Phase 1: P1 — Hybrid Retrieval + Rerank (1 week)
**Goal:** Retrieval quality that exceeds basic vector search.
- OpenSearch BM25 index configuration + field weighting
- Parallel BM25 + kNN retrieval
- RRF fusion implementation (k=60)
- Qwen rerank integration with degradation path (skip on failure)
- Structure-aware chunking upgrade
- Fallback design: rerank failure → return RRF results directly

### Phase 2: Enhancers — Multi-Turn + Caching (3-5 days)
**Goal:** Production-grade Q&A experience.
- Query rewriting with dual-query fallback
- Conversation memory (PostgreSQL + ChatMemory)
- Citation generation with source validation
- L1/L2 caching for embeddings, retrieval results, rerank scores
- Semantic cache (with documented threshold calibration)

### Phase 3: Enhancers — Observability + Evaluation (3-5 days)
**Goal:** Prove the system works with metrics.
- Prometheus metrics for all pipeline stages
- Golden set evaluation script (recall@5, rerank shift rate)
- Admin stats endpoint
- Docker Compose full stack for demo

### Phase 4 (Optional): Advanced Differentiators
- Knowledge freshness: version lifecycle, chunk TTL, cold/hot separation
- Parent-child chunking (small-to-big retrieval) — hierarchical retrieval
- Management dashboard for document upload and evaluation results

## Demo Flow

1. **Upload**: Upload 3-5 enterprise documents via API
2. **Ingestion**: Chunks visible in OpenSearch dashboard with metadata
3. **Single-turn Q&A**: Factual question → answer with `[文档名 > 章节路径]` citations
4. **Multi-turn Q&A**: Follow-up with pronouns → system rewrites query correctly
5. **Hybrid retrieval**: Product codes (BM25) and semantic questions (vector) both return results
6. **Rerank effect**: Same query with/without reranking shows quality difference
7. **Cache hit**: Second identical query returns near-instantly
8. **Evaluation**: recall@5 and latency metrics via Prometheus endpoint

## Design Decisions & Trade-offs

| Decision | Chosen | Alternative | Reasoning | Fallback on Failure |
|---|---|---|---|---|
| Hybrid retrieval fusion | RRF | Weighted sum | RRF avoids scale incompatibility between BM25 and cosine | — (internal logic, no external dependency) |
| Final ranking | Reranker only | RRF + α·rerank | Mixing scores re-introduces scale problem; two-stage paradigm keeps stages independent | Skip rerank; return RRF results directly |
| Embedding provider | Qwen (self-hosted) | OpenAI API | Zero marginal cost; full control; demonstrates infra capability | Use cached embeddings; queue new ingestion |
| Generation provider | DeepSeek API | GPT-4 | Cost-effective; strong reasoning; differentiates from OpenAI-default projects | Return raw chunks without synthesis |
| Chunk storage | OpenSearch | PostgreSQL + pgvector | OpenSearch native BM25+vector hybrid; no second system for词法检索 | Ingestion paused; cache hits still work |
| Query rewriting | LLM-based with fallback | Rule-based | LLM handles Chinese pronouns/context better; fallback prevents single point of failure | Use original query directly |
| Semantic cache | Embedding similarity | Exact match | Handles paraphrased queries; common in enterprise Q&A | Fall back to exact-match cache |
| Evaluation | Golden set script | RAGAS framework | Lightweight; sufficient for portfolio demo; avoids Python dependency | Manual spot-checking |
