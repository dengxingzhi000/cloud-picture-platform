CREATE TABLE notification_record (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    kind VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT,
    target_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_user_created ON notification_record(user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification_record(user_id, is_read) WHERE is_read = FALSE;
