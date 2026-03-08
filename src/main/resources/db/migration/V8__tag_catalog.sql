create table tag (
    id uuid primary key,
    name varchar(80) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_tag_name unique (name)
);

create index idx_tag_name on tag (name);

alter table picture_tag add column tag_id uuid;
alter table picture_tag add constraint fk_picture_tag_catalog foreign key (tag_id) references tag(id);
create index idx_picture_tag_tag on picture_tag (tag_id);
