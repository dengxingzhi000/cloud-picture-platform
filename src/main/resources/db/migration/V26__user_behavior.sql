-- User behavior events for the data flywheel
-- Records view, download, share, search, tag feedback activity

create table user_behavior_event (
    id              uuid          primary key,
    user_id         uuid          not null,
    event_type      varchar(50)   not null,
    picture_id      uuid,
    session_id      varchar(64),
    search_query    text,
    tag_text        varchar(100),
    tag_feedback    varchar(20),
    duration_ms     integer,
    metadata        jsonb,
    created_at      timestamptz   not null
);

create index idx_ube_user_event on user_behavior_event (user_id, event_type, created_at);
create index idx_ube_picture    on user_behavior_event (picture_id) where picture_id is not null;
create index idx_ube_type_time  on user_behavior_event (event_type, created_at);

-- Tag feedback for AI accuracy tracking
create table tag_feedback (
    id              uuid          primary key,
    picture_id      uuid          not null,
    tag_text        varchar(100)  not null,
    user_id         uuid          not null,
    feedback        varchar(20)   not null,  -- ACCEPT / REJECT
    provider        varchar(50),
    confidence_score double precision,
    created_at      timestamptz   not null,
    constraint uq_tag_feedback unique (picture_id, tag_text, user_id)
);

create index idx_tf_picture on tag_feedback (picture_id);
create index idx_tf_provider on tag_feedback (provider, created_at);
