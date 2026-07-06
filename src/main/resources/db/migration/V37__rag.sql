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
