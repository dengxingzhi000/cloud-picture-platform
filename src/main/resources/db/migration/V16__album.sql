-- Album (相册) table for organizing pictures

create table album (
    id              uuid         primary key,
    space_id        uuid         not null,
    name            varchar(100) not null,
    description     varchar(500),
    cover_picture_id uuid,
    visibility      varchar(20)  not null default 'PRIVATE',
    sort_order      integer      not null default 0,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create index idx_album_space on album (space_id);
create index idx_album_visibility on album (visibility);

create table album_picture (
    id              uuid         primary key,
    album_id        uuid         not null references album(id) on delete cascade,
    picture_id      uuid         not null,
    sort_order      integer      not null default 0,
    created_at      timestamptz  not null
);

create index idx_album_picture_album on album_picture (album_id);
create index idx_album_picture_picture on album_picture (picture_id);
create unique index idx_album_picture_unique on album_picture (album_id, picture_id);
