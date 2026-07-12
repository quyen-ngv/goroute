CREATE TABLE IF NOT EXISTS place_import_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref_id UUID,
    status VARCHAR(32) NOT NULL,
    max_reviews INTEGER NOT NULL DEFAULT 5,
    total_items INTEGER NOT NULL DEFAULT 0,
    skipped_existing_count INTEGER NOT NULL DEFAULT 0,
    triggered_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    request_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_place_import_jobs_user_created_at
    ON place_import_jobs(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_place_import_jobs_status
    ON place_import_jobs(status);

CREATE TABLE IF NOT EXISTS place_import_job_items (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    source_ref_id UUID,
    activity_id UUID,
    source_url TEXT,
    candidate_name TEXT,
    google_place_id VARCHAR(255),
    cid VARCHAR(255),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    existing_place_id UUID,
    imported_place_id UUID,
    python_job_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_place_import_job_items_job_id
    ON place_import_job_items(job_id);

CREATE INDEX IF NOT EXISTS idx_place_import_job_items_python_job_id
    ON place_import_job_items(python_job_id);

CREATE INDEX IF NOT EXISTS idx_place_import_job_items_activity_id
    ON place_import_job_items(activity_id);
