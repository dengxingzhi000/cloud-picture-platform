-- Webhook endpoint management

create table webhook_endpoint (
    id              uuid         primary key,
    owner_id        uuid         not null,
    owner_type      varchar(10)  not null,     -- 'USER' / 'TEAM'
    url             varchar(500) not null,
    secret          varchar(100) not null,
    events          text         not null,     -- JSON array of event types
    active          boolean      not null default true,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create index idx_we_owner on webhook_endpoint (owner_id, owner_type);

create table webhook_delivery (
    id              uuid         primary key,
    webhook_id      uuid         not null,
    event_type      varchar(50)  not null,
    request_url     varchar(500) not null,
    request_body    text,
    response_status integer,
    response_body   text,
    success         boolean      not null,
    duration_ms     integer,
    created_at      timestamptz  not null
);

create index idx_wd_webhook on webhook_delivery (webhook_id, created_at desc);
