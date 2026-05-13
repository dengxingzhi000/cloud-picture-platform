-- =====================================================
-- Cloud Picture Platform — PostgreSQL baseline schema
-- Consolidates V1-V12 H2 migrations into native PG DDL
-- =====================================================

-- ── 1. app_user ────────────────────────────────────────

create table app_user (
    id            uuid        primary key,
    username      varchar(64)  not null,
    email         varchar(120),
    password_hash varchar(200) not null,
    display_name  varchar(80),
    avatar_url    varchar(500),
    status        varchar(20)  not null,
    role          varchar(20)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint uk_app_user_username unique (username),
    constraint uk_app_user_email    unique (email)
);

create index idx_user_username on app_user (username);
create index idx_user_email    on app_user (email);

-- ── 2. team ────────────────────────────────────────────

create table team (
    id          uuid         primary key,
    owner_id    uuid         not null,
    name        varchar(80)  not null,
    description varchar(200),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint fk_team_owner foreign key (owner_id) references app_user(id)
);

create index idx_team_owner on team (owner_id);

-- ── 3. picture_space ───────────────────────────────────

create table picture_space (
    id         uuid        primary key,
    owner_id   uuid        not null,
    team_id    uuid,
    type       varchar(20) not null,
    name       varchar(80) not null,
    quota_bytes bigint      not null,
    used_bytes  bigint      not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_space_owner foreign key (owner_id) references app_user(id),
    constraint fk_space_team  foreign key (team_id)  references team(id)
);

create index idx_space_owner on picture_space (owner_id);
create index idx_space_type  on picture_space (type);
create index idx_space_team  on picture_space (team_id);

-- ── 4. team_member ─────────────────────────────────────

create table team_member (
    id         uuid        primary key,
    team_id    uuid        not null,
    user_id    uuid        not null,
    role       varchar(20) not null,
    status     varchar(20) not null,
    invited_by uuid,
    invited_at timestamptz,
    joined_at  timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_team_member_team    foreign key (team_id)    references team(id),
    constraint fk_team_member_user    foreign key (user_id)    references app_user(id),
    constraint fk_team_member_inviter foreign key (invited_by) references app_user(id),
    constraint uk_team_member         unique (team_id, user_id)
);

create index idx_team_member_team   on team_member (team_id);
create index idx_team_member_user   on team_member (user_id);
create index idx_team_member_status on team_member (status);

-- ── 5. team_member_event ───────────────────────────────

create table team_member_event (
    id         uuid         primary key,
    team_id    uuid         not null,
    user_id    uuid,
    actor_id   uuid,
    type       varchar(30)  not null,
    role       varchar(20),
    detail     varchar(1000),
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    constraint fk_team_member_event_team  foreign key (team_id)  references team(id),
    constraint fk_team_member_event_user  foreign key (user_id)  references app_user(id),
    constraint fk_team_member_event_actor foreign key (actor_id) references app_user(id)
);

create index idx_team_member_event_team  on team_member_event (team_id);
create index idx_team_member_event_user  on team_member_event (user_id);
create index idx_team_member_event_actor on team_member_event (actor_id);
create index idx_team_member_event_type  on team_member_event (type);

-- ── 6. file_content (deduplication) ────────────────────

create table file_content (
    id                uuid         primary key,
    sha256_hash       varchar(64)  not null,
    perceptual_hash   varchar(16),
    diff_hash         varchar(16),
    size_bytes        bigint       not null,
    content_type      varchar(120),
    storage_key       varchar(500) not null,
    url               varchar(500),
    width             integer,
    height            integer,
    ref_count         integer      not null default 1,
    original_filename varchar(200),
    first_uploader_id uuid,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint uk_file_sha256 unique (sha256_hash),
    constraint fk_file_first_uploader
        foreign key (first_uploader_id) references app_user(id) on delete set null
);

create index idx_file_sha256    on file_content (sha256_hash);
create index idx_file_phash     on file_content (perceptual_hash) where perceptual_hash is not null;
create index idx_file_dhash     on file_content (diff_hash)       where diff_hash is not null;
create index idx_file_size      on file_content (size_bytes);
create index idx_file_ref_count on file_content (ref_count)       where ref_count <= 0;

-- ── 7. picture_asset ───────────────────────────────────

create table picture_asset (
    id               uuid         primary key,
    owner_id         uuid         not null,
    space_id         uuid         not null,
    file_content_id  uuid,
    visibility       varchar(20)  not null,
    review_status    varchar(20)  not null,
    name             varchar(200) not null,
    original_filename varchar(200) not null,
    content_type     varchar(120),
    size_bytes       bigint       not null,
    checksum         varchar(64),
    storage_key      varchar(200) not null,
    url              varchar(500),
    width            integer,
    height           integer,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    constraint fk_picture_owner       foreign key (owner_id)        references app_user(id),
    constraint fk_picture_space       foreign key (space_id)        references picture_space(id),
    constraint fk_picture_file_content foreign key (file_content_id) references file_content(id) on delete set null
);

create index idx_picture_owner       on picture_asset (owner_id);
create index idx_picture_space       on picture_asset (space_id);
create index idx_picture_visibility  on picture_asset (visibility);
create index idx_picture_review      on picture_asset (review_status);
create index idx_picture_file_content on picture_asset (file_content_id);

-- ── 8. moderation_record ───────────────────────────────

create table moderation_record (
    id          uuid        primary key,
    picture_id  uuid        not null,
    reviewer_id uuid        not null,
    from_status varchar(20) not null,
    to_status   varchar(20) not null,
    reason      varchar(500),
    reviewed_at timestamptz not null,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint fk_moderation_picture  foreign key (picture_id)  references picture_asset(id),
    constraint fk_moderation_reviewer foreign key (reviewer_id) references app_user(id)
);

create index idx_moderation_picture  on moderation_record (picture_id);
create index idx_moderation_reviewer on moderation_record (reviewer_id);

-- ── 9. tag ─────────────────────────────────────────────

create table tag (
    id         uuid        primary key,
    name       varchar(80) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_tag_name unique (name)
);

create index idx_tag_name on tag (name);

-- ── 10. picture_tag ────────────────────────────────────

create table picture_tag (
    id               uuid          primary key,
    picture_asset_id uuid          not null,
    tag_id           uuid,
    tag_text         varchar(100)  not null,
    confidence_score double precision,
    provider         varchar(50),
    is_auto_generated boolean       not null default true,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    constraint fk_picture_tag_asset  foreign key (picture_asset_id) references picture_asset(id),
    constraint fk_picture_tag_catalog foreign key (tag_id)          references tag(id)
);

create index idx_picture_tag_asset on picture_tag (picture_asset_id);
create index idx_picture_tag_text  on picture_tag (tag_text);
create index idx_picture_tag_tag   on picture_tag (tag_id);

-- ── 11. picture_search_document ────────────────────────

create table picture_search_document (
    picture_id uuid        primary key,
    content    text        not null,
    updated_at timestamptz not null
);

create index idx_picture_search_updated on picture_search_document (updated_at);

-- ── 12. picture_editor_document ────────────────────────

create table picture_editor_document (
    id                      uuid        primary key,
    picture_id              uuid        not null,
    version                 bigint      not null default 0,
    row_version             bigint      not null default 0,
    document_content        text        not null,
    last_updated_by_user_id uuid,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    constraint fk_picture_editor_document_picture
        foreign key (picture_id) references picture_asset(id) on delete cascade,
    constraint uk_picture_editor_document_picture unique (picture_id)
);

create index idx_picture_editor_document_picture
    on picture_editor_document (picture_id);
