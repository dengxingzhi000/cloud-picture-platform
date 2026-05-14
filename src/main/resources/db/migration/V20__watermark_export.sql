-- Watermark configuration and export system

create table watermark_config (
    id                 uuid         primary key,
    team_id            uuid         not null unique,
    enabled            boolean      not null default false,
    type               varchar(10)  not null default 'TEXT',  -- TEXT / IMAGE
    text               varchar(200),
    image_storage_key  varchar(500),
    opacity            float        not null default 0.5,
    position           varchar(30)  not null default 'BOTTOM_RIGHT',
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null
);

create table export_preset (
    id                 uuid         primary key,
    team_id            uuid         not null,
    name               varchar(100) not null,
    target_width       integer      not null,
    target_height      integer      not null,
    format             varchar(10)  not null default 'JPEG',
    quality            integer      not null default 90,
    watermark_enabled  boolean      not null default true,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null
);

create index idx_ep_team on export_preset (team_id);

create table export_task (
    id                 uuid         primary key,
    picture_id         uuid         not null,
    preset_id          uuid,
    user_id            uuid         not null,
    status             varchar(20)  not null default 'PENDING',
    result_url         varchar(500),
    error_message      text,
    created_at         timestamptz  not null,
    completed_at       timestamptz
);

create index idx_et_user on export_task (user_id, created_at desc);
