create table app_user (
    id uuid primary key,
    username varchar(64) not null,
    email varchar(120),
    password_hash varchar(200) not null,
    display_name varchar(80),
    status varchar(20) not null,
    role varchar(20) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_app_user_username unique (username),
    constraint uk_app_user_email unique (email)
);

create table picture_space (
    id uuid primary key,
    owner_id uuid not null,
    type varchar(20) not null,
    name varchar(80) not null,
    quota_bytes bigint not null,
    used_bytes bigint not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_space_owner foreign key (owner_id) references app_user(id)
);

create table picture_asset (
    id uuid primary key,
    owner_id uuid not null,
    space_id uuid not null,
    visibility varchar(20) not null,
    review_status varchar(20) not null,
    name varchar(200) not null,
    original_filename varchar(200) not null,
    content_type varchar(120),
    size_bytes bigint not null,
    checksum varchar(64),
    storage_key varchar(200) not null,
    url varchar(500),
    width integer,
    height integer,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_picture_owner foreign key (owner_id) references app_user(id),
    constraint fk_picture_space foreign key (space_id) references picture_space(id)
);


create index idx_user_username on app_user (username);
create index idx_user_email on app_user (email);

create index idx_space_owner on picture_space (owner_id);
create index idx_space_type on picture_space (type);

create index idx_picture_owner on picture_asset (owner_id);
create index idx_picture_space on picture_asset (space_id);
create index idx_picture_visibility on picture_asset (visibility);
create index idx_picture_review on picture_asset (review_status);
