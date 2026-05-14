-- Outbox table for reliable event processing (transactional outbox pattern)
-- Events are written within the business transaction, then processed asynchronously

create table business_event_outbox (
    id              uuid         primary key,
    aggregate_type  varchar(50)  not null,
    aggregate_id    uuid         not null,
    event_type      varchar(50)  not null,
    payload         text,
    status          varchar(20)  not null default 'PENDING',
    retry_count     int          not null default 0,
    created_at      timestamptz  not null,
    processed_at    timestamptz
);

create index idx_outbox_status_created on business_event_outbox (status, created_at)
    where status = 'PENDING';
