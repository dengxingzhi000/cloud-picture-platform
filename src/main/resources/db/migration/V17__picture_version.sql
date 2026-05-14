-- Picture version history for tracking file changes over time

create table picture_version (
    id                uuid         primary key,
    picture_id        uuid         not null,
    version           integer      not null,
    storage_key       varchar(500) not null,
    file_content_id   uuid,
    file_size         bigint       not null,
    width             integer,
    height            integer,
    checksum          varchar(64),
    change_note       varchar(500),
    created_by_user_id uuid        not null,
    created_at        timestamptz  not null
);

create index idx_pv_picture on picture_version (picture_id);
create index idx_pv_version on picture_version (picture_id, version);
