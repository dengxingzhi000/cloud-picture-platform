-- Extend ai_call_audit with observability fields
ALTER TABLE ai_call_audit ADD COLUMN tokens_used INTEGER;
ALTER TABLE ai_call_audit ADD COLUMN tool_calls_count INTEGER;
ALTER TABLE ai_call_audit ADD COLUMN request_summary VARCHAR(500);

-- Create ai_tool_call_audit table
CREATE TABLE ai_tool_call_audit (
    id UUID NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    user_id UUID,
    tool_name VARCHAR(100) NOT NULL,
    tool_args TEXT,
    result_size INTEGER,
    success BOOLEAN NOT NULL,
    latency_ms INTEGER,
    error_message TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_atca_session ON ai_tool_call_audit(session_id, created_at);
CREATE INDEX idx_atca_tool ON ai_tool_call_audit(tool_name, created_at);
CREATE INDEX idx_atca_user ON ai_tool_call_audit(user_id, created_at);
