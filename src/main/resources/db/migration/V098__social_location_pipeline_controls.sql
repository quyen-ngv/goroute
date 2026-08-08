ALTER TABLE social_location_jobs
    ADD COLUMN IF NOT EXISTS user_tier VARCHAR(32),
    ADD COLUMN IF NOT EXISTS video_duration_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS max_duration_seconds INTEGER;

DROP INDEX IF EXISTS uq_social_location_jobs_user_source_key_reusable;
CREATE UNIQUE INDEX IF NOT EXISTS uq_social_location_jobs_user_source_key_reusable
    ON social_location_jobs(user_id, source_key)
    WHERE source_key IS NOT NULL
      AND status IN ('QUEUED', 'DISPATCHING', 'PROCESSING', 'COMPLETED');

CREATE TABLE IF NOT EXISTS social_location_user_restrictions (
    user_id UUID PRIMARY KEY,
    strike_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    blocked_until TIMESTAMP,
    reason_code VARCHAR(128),
    reason_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_social_location_strikes CHECK (strike_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_social_location_restrictions_status
    ON social_location_user_restrictions(status, blocked_until, updated_at DESC);

CREATE TABLE IF NOT EXISTS social_location_submission_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    job_id UUID REFERENCES social_location_jobs(id) ON DELETE SET NULL,
    source_url TEXT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    reason_code VARCHAR(128),
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_social_location_events_user_created
    ON social_location_submission_events(user_id, created_at DESC);

INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('SOCIAL_LOCATION', 'DAILY_JOB_LIMIT_DEFAULT', '5', 'Maximum social video submissions per user per day', TRUE),
    ('SOCIAL_LOCATION', 'MAX_CONCURRENT_JOBS', '5', 'Maximum social-location jobs processed concurrently', TRUE),
    ('SOCIAL_LOCATION', 'MAX_QUEUED_JOBS', '100', 'Maximum queued social-location jobs across the system', TRUE),
    ('SOCIAL_LOCATION', 'MAX_VIDEO_SECONDS_DEFAULT', '180', 'Maximum social video duration for FREE/default users', TRUE),
    ('SOCIAL_LOCATION', 'MAX_VIDEO_SECONDS_PRO', '300', 'Maximum social video duration for PRO users', TRUE),
    ('SOCIAL_LOCATION', 'FRAME_INTERVAL_SECONDS', '3', 'Seconds between extracted video frames', TRUE),
    ('SOCIAL_LOCATION', 'IMAGE_MAX_WIDTH', '320', 'Maximum frame width sent to the extraction model', TRUE),
    ('SOCIAL_LOCATION', 'IMAGE_JPEG_QUALITY', '18', 'FFmpeg JPEG q:v; higher values produce smaller images', TRUE),
    ('SOCIAL_LOCATION', 'FIRST_BLOCK_MINUTES', '10', 'First abuse cooldown duration in minutes', TRUE),
    ('SOCIAL_LOCATION', 'SECOND_BLOCK_HOURS', '24', 'Second abuse block duration in hours', TRUE),
    ('SOCIAL_LOCATION', 'PERMANENT_BLOCK_STRIKES', '3', 'Strike count that permanently disables the feature', TRUE)
ON CONFLICT (label, key) DO NOTHING;
