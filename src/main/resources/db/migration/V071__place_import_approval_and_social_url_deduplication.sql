-- Additive migration: existing import rows already affected user data, so they
-- remain approved. Only newly-created rows enter the moderation queue.
ALTER TABLE social_location_jobs
    ADD COLUMN IF NOT EXISTS source_key VARCHAR(512);

CREATE UNIQUE INDEX IF NOT EXISTS uq_social_location_jobs_user_source_key_reusable
    ON social_location_jobs(user_id, source_key)
    WHERE source_key IS NOT NULL
      AND status IN ('QUEUED', 'PROCESSING', 'COMPLETED');

ALTER TABLE place_import_job_items
    ADD COLUMN IF NOT EXISTS source_candidate_key VARCHAR(768),
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS approval_note TEXT,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_place_import_items_approval_status
    ON place_import_job_items(approval_status, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_place_import_social_candidate_mapping
    ON place_import_job_items(source_ref_id, source_candidate_key)
    WHERE activity_id IS NULL
      AND source_ref_id IS NOT NULL
      AND source_candidate_key IS NOT NULL;
