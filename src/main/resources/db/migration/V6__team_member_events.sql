create table team_member_event (
    id uuid primary key,
    team_id uuid not null,
    user_id uuid,
    actor_id uuid,
    type varchar(30) not null,
    role varchar(20),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_team_member_event_team foreign key (team_id) references team(id),
    constraint fk_team_member_event_user foreign key (user_id) references app_user(id),
    constraint fk_team_member_event_actor foreign key (actor_id) references app_user(id)
);

create index idx_team_member_event_team on team_member_event (team_id);
create index idx_team_member_event_user on team_member_event (user_id);
create index idx_team_member_event_actor on team_member_event (actor_id);
create index idx_team_member_event_type on team_member_event (type);
