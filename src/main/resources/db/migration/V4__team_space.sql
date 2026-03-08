create table team (
    id uuid primary key,
    owner_id uuid not null,
    name varchar(80) not null,
    description varchar(200),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_team_owner foreign key (owner_id) references app_user(id)
);

create index idx_team_owner on team (owner_id);

create table team_member (
    id uuid primary key,
    team_id uuid not null,
    user_id uuid not null,
    role varchar(20) not null,
    status varchar(20) not null,
    invited_by uuid,
    invited_at timestamp,
    joined_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_team_member_team foreign key (team_id) references team(id),
    constraint fk_team_member_user foreign key (user_id) references app_user(id),
    constraint fk_team_member_inviter foreign key (invited_by) references app_user(id),
    constraint uk_team_member unique (team_id, user_id)
);

create index idx_team_member_team on team_member (team_id);
create index idx_team_member_user on team_member (user_id);
create index idx_team_member_status on team_member (status);

alter table picture_space add column team_id uuid;
alter table picture_space add constraint fk_space_team foreign key (team_id) references team(id);

create index idx_space_team on picture_space (team_id);
