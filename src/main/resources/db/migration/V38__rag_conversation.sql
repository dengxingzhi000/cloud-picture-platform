CREATE TABLE rag_conversation (
    id UUID NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_rag_conv_session ON rag_conversation(session_id, created_at);