-- Picture comment / annotation system

create table picture_comment (
    id              uuid         primary key,
    picture_id      uuid         not null,
    author_id       uuid         not null,
    content         text         not null,
    parent_id       uuid,
    x               double precision,
    y               double precision,
    resolved        boolean      not null default false,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create index idx_pc_picture on picture_comment (picture_id);
create index idx_pc_parent on picture_comment (parent_id);
