alter table picture_editor_document
    add column row_version bigint not null default 0;
