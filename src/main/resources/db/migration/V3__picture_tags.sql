create table picture_tag (
    id uuid primary key,
    picture_asset_id uuid not null,
    tag_text varchar(100) not null,
    confidence_score double,
    provider varchar(50),
    is_auto_generated boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_picture_tag_asset foreign key (picture_asset_id) references picture_asset(id)
);

create index idx_picture_tag_asset on picture_tag (picture_asset_id);
create index idx_picture_tag_text on picture_tag (tag_text);
