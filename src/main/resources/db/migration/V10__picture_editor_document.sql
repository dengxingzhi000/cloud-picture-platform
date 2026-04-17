-- =====================================================
-- V10: picture editor document table
-- =====================================================

create table picture_editor_document (
    id uuid primary key,
    picture_id uuid not null,
    version bigint not null default 0,
    document_content text not null,
    last_updated_by_user_id uuid,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint fk_picture_editor_document_picture
        foreign key (picture_id) references picture_asset(id) on delete cascade,
    constraint uk_picture_editor_document_picture unique (picture_id)
);

create index idx_picture_editor_document_picture
    on picture_editor_document (picture_id);
