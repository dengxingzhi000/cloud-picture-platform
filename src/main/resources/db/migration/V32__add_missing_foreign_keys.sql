-- V32: Add missing foreign key constraints
-- These were omitted from V17-V28 migrations

-- Album references
ALTER TABLE album ADD CONSTRAINT fk_album_space
    FOREIGN KEY (space_id) REFERENCES picture_space(id);
ALTER TABLE album ADD CONSTRAINT fk_album_cover_picture
    FOREIGN KEY (cover_picture_id) REFERENCES picture_asset(id);

-- Album-Picture junction
ALTER TABLE album_picture ADD CONSTRAINT fk_album_picture_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);

-- Picture Version
ALTER TABLE picture_version ADD CONSTRAINT fk_picture_version_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE picture_version ADD CONSTRAINT fk_picture_version_file_content
    FOREIGN KEY (file_content_id) REFERENCES file_content(id);
ALTER TABLE picture_version ADD CONSTRAINT fk_picture_version_created_by
    FOREIGN KEY (created_by_user_id) REFERENCES app_user(id);

-- Picture Comment
ALTER TABLE picture_comment ADD CONSTRAINT fk_picture_comment_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE picture_comment ADD CONSTRAINT fk_picture_comment_author
    FOREIGN KEY (author_id) REFERENCES app_user(id);
ALTER TABLE picture_comment ADD CONSTRAINT fk_picture_comment_parent
    FOREIGN KEY (parent_id) REFERENCES picture_comment(id);

-- Team Activity
ALTER TABLE team_activity ADD CONSTRAINT fk_team_activity_team
    FOREIGN KEY (team_id) REFERENCES team(id);
ALTER TABLE team_activity ADD CONSTRAINT fk_team_activity_actor
    FOREIGN KEY (actor_id) REFERENCES app_user(id);

-- Export
ALTER TABLE export_task ADD CONSTRAINT fk_export_task_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE export_task ADD CONSTRAINT fk_export_task_preset
    FOREIGN KEY (preset_id) REFERENCES export_preset(id);
ALTER TABLE export_task ADD CONSTRAINT fk_export_task_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

-- Webhook
ALTER TABLE webhook_delivery ADD CONSTRAINT fk_webhook_delivery_webhook
    FOREIGN KEY (webhook_id) REFERENCES webhook_endpoint(id);

-- API Key
ALTER TABLE api_key ADD CONSTRAINT fk_api_key_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

-- AI tables
ALTER TABLE ai_call_audit ADD CONSTRAINT fk_ai_call_audit_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE ai_call_audit ADD CONSTRAINT fk_ai_call_audit_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE ai_task ADD CONSTRAINT fk_ai_task_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE ai_moderation_record ADD CONSTRAINT fk_ai_moderation_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);

-- Notification
ALTER TABLE notification_record ADD CONSTRAINT fk_notification_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

-- Excalidraw
ALTER TABLE excalidraw_scene ADD CONSTRAINT fk_excalidraw_scene_picture
    FOREIGN KEY (picture_id) REFERENCES picture_asset(id);
ALTER TABLE excalidraw_scene ADD CONSTRAINT fk_excalidraw_scene_updated_by
    FOREIGN KEY (last_updated_by_user_id) REFERENCES app_user(id);
