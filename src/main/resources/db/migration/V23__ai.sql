-- AI service audit and task tracking

create table ai_call_audit (
    id              uuid         primary key,
    task_type       varchar(50)  not null,
    picture_id      uuid,
    user_id         uuid,
    provider        varchar(100),
    model_version   varchar(50),
    success         boolean      not null,
    latency_ms      integer,
    cost_units      integer,
    error_code      varchar(50),
    created_at      timestamptz  not null
);

create index idx_aca_type on ai_call_audit (task_type, created_at);

create table ai_task (
    id              uuid         primary key,
    task_type       varchar(50)  not null,
    picture_id      uuid,
    status          varchar(20)  not null default 'PENDING',
    submitted_at    timestamptz  not null,
    started_at      timestamptz,
    completed_at    timestamptz,
    retry_count     integer      not null default 0,
    error_message   text,
    result_summary  text
);

create index idx_at_status on ai_task (status, submitted_at);
