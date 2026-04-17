-- =====================================================
-- V11: file content deduplication
-- =====================================================

create table file_content (
    id uuid primary key,
    sha256_hash text not null,
    perceptual_hash text,
    diff_hash text,
    size_bytes bigint not null,
    content_type text,
    storage_key text not null,
    url text,
    width integer,
    height integer,
    ref_count integer not null default 1,
    original_filename text,
    first_uploader_id uuid,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uk_file_sha256 unique (sha256_hash),
    constraint fk_file_first_uploader
        foreign key (first_uploader_id) references app_user(id) on delete set null
);

create index idx_file_sha256 on file_content (sha256_hash);
create index idx_file_phash on file_content (perceptual_hash)
    where perceptual_hash is not null;
create index idx_file_dhash on file_content (diff_hash)
    where diff_hash is not null;
create index idx_file_size on file_content (size_bytes);
create index idx_file_ref_count on file_content (ref_count)
    where ref_count <= 0;

alter table picture_asset
    add column file_content_id uuid;

alter table picture_asset
    add constraint fk_picture_file_content
        foreign key (file_content_id) references file_content(id) on delete set null;

create index idx_picture_file_content on picture_asset (file_content_id);
