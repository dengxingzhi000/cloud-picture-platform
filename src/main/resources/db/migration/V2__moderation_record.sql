create table moderation_record (
    id uuid primary key,
    picture_id uuid not null,
    reviewer_id uuid not null,
    from_status varchar(20) not null,
    to_status varchar(20) not null,
    reason varchar(500),
    reviewed_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_moderation_picture foreign key (picture_id) references picture_asset(id),
    constraint fk_moderation_reviewer foreign key (reviewer_id) references app_user(id)
);

create index idx_moderation_picture on moderation_record (picture_id);
create index idx_moderation_reviewer on moderation_record (reviewer_id);
