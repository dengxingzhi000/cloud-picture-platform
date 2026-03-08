create table picture_search_document (
    picture_id uuid primary key,
    content text not null,
    updated_at timestamp not null
);

create index idx_picture_search_updated on picture_search_document (updated_at);
