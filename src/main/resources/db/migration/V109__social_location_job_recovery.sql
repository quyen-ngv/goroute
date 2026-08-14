ALTER TABLE social_location_jobs
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deadline_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_reconciled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS failure_stage VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_details JSONB;

UPDATE social_location_jobs
SET deadline_at = updated_at + INTERVAL '15 minutes'
WHERE status IN ('DISPATCHING', 'PROCESSING')
  AND deadline_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_social_location_jobs_recovery
    ON social_location_jobs(status, deadline_at, last_reconciled_at)
    WHERE status IN ('DISPATCHING', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_social_location_jobs_retry
    ON social_location_jobs(status, next_attempt_at, created_at)
    WHERE status = 'QUEUED';
