-- pgvector support for semantic image search
-- Requires: CREATE EXTENSION IF NOT EXISTS vector;

alter table picture_search_document
    add column if not exists image_embedding vector(768);

create index if not exists idx_psd_embedding
    on picture_search_document
    using ivfflat (image_embedding vector_cosine_ops)
    with (lists = 100);
