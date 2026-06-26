CREATE TABLE ai_chat_history (
    id             UUID PRIMARY KEY,
    session_id     VARCHAR(100)  NOT NULL,
    user_id        UUID          NOT NULL,
    role           VARCHAR(20)   NOT NULL,
    content        TEXT          NOT NULL,
    intent         VARCHAR(50),
    context_picture_ids TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_ach_session ON ai_chat_history (session_id, created_at);
CREATE INDEX idx_ach_user    ON ai_chat_history (user_id, created_at);
