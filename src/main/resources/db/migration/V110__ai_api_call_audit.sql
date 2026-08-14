CREATE TABLE IF NOT EXISTS ai_api_calls (
    id UUID PRIMARY KEY,
    user_id UUID,
    feature VARCHAR(64) NOT NULL,
    operation VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128),
    provider VARCHAR(64),
    model VARCHAR(200),
    status VARCHAR(32) NOT NULL,
    request_payload JSONB,
    response_payload JSONB,
    error_payload JSONB,
    candidate_count INTEGER,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms BIGINT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_api_calls_candidate_count CHECK (candidate_count IS NULL OR candidate_count >= 0),
    CONSTRAINT chk_ai_api_calls_tokens CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
        AND (total_tokens IS NULL OR total_tokens >= 0)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_api_calls_correlation
    ON ai_api_calls(feature, operation, correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_api_calls_user_created
    ON ai_api_calls(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_api_calls_feature_status_created
    ON ai_api_calls(feature, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_api_calls_provider_model_created
    ON ai_api_calls(provider, model, created_at DESC);
