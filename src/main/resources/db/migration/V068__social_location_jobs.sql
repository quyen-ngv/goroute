CREATE TABLE IF NOT EXISTS social_location_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_url TEXT NOT NULL,
    platform VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    python_job_id VARCHAR(128),
    language VARCHAR(20),
    request_payload TEXT,
    result_payload TEXT,
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_social_location_jobs_user_id_created_at
    ON social_location_jobs(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_social_location_jobs_python_job_id
    ON social_location_jobs(python_job_id);

CREATE INDEX IF NOT EXISTS idx_social_location_jobs_status
    ON social_location_jobs(status);
