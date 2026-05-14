-- Team activity stream

create table team_activity (
    id              uuid         primary key,
    team_id         uuid         not null,
    actor_id        uuid         not null,
    activity_type   varchar(50)  not null,
    target_id       uuid,
    payload         jsonb,
    created_at      timestamptz  not null
);

create index idx_ta_team on team_activity (team_id, created_at desc);
