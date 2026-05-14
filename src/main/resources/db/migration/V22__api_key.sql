-- API Key management for developer portal

create table api_key (
    id              uuid         primary key,
    user_id         uuid         not null,
    name            varchar(100) not null,
    key_prefix      varchar(8)   not null,
    key_hash        varchar(64)  not null,
    scopes          text         not null,   -- JSON array: ["picture:read","picture:write"]
    rate_limit      integer      not null default 100,
    expires_at      timestamptz,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create index idx_ak_user on api_key (user_id);
