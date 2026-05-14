-- AI moderation audit trail

create table ai_moderation_record (
    id                uuid         primary key,
    picture_id        uuid         not null,
    provider          varchar(100),
    model_version     varchar(50),
    is_safe           boolean,
    confidence        double precision,
    violation_categories text,
    raw_response      text,
    processing_ms     integer,
    created_at        timestamptz  not null
);

create index idx_amr_picture on ai_moderation_record (picture_id, created_at desc);
