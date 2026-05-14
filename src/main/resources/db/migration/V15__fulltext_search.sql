-- Full-text search support using PostgreSQL tsvector + GIN index
-- Enables efficient text search across picture search documents

alter table picture_search_document
    add column if not exists content_tsv tsvector
    generated always as (to_tsvector('simple', coalesce(content, ''))) stored;

create index if not exists idx_psd_content_tsv
    on picture_search_document
    using gin (content_tsv);
