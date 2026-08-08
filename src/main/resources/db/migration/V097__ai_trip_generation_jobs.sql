ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS end_day_number INTEGER;

CREATE TABLE ai_trip_generation_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    request_payload JSONB NOT NULL,
    locale VARCHAR(16) NOT NULL DEFAULT 'en',
    attempt_id VARCHAR(80) NOT NULL,
    python_job_id VARCHAR(120),
    status VARCHAR(24) NOT NULL,
    stage VARCHAR(48) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    quota_status VARCHAR(16) NOT NULL,
    created_trip_id UUID,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    CONSTRAINT uq_ai_trip_generation_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_ai_trip_generation_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_ai_trip_generation_quota CHECK (
        quota_status IN ('RESERVED', 'CONSUMED', 'RELEASED')
    ),
    CONSTRAINT chk_ai_trip_generation_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE INDEX idx_ai_trip_generation_user_status
    ON ai_trip_generation_jobs(user_id, status, updated_at DESC);

CREATE TABLE ai_trip_generation_events (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_trip_generation_jobs(id) ON DELETE CASCADE,
    attempt_id VARCHAR(80) NOT NULL,
    stage VARCHAR(48) NOT NULL,
    status VARCHAR(24) NOT NULL,
    progress INTEGER NOT NULL,
    message_key VARCHAR(160),
    params JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_trip_generation_events_job_id
    ON ai_trip_generation_events(job_id, id);
