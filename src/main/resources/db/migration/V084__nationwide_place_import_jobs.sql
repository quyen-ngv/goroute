ALTER TABLE place_import_jobs
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE place_import_jobs
    ADD COLUMN IF NOT EXISTS python_job_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS current_region_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS current_region_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS processed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS eligible_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS imported_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rejected_score_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS insufficient_photo_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS selected_reviews INTEGER NOT NULL DEFAULT 20,
    ADD COLUMN IF NOT EXISTS min_review_count INTEGER NOT NULL DEFAULT 101,
    ADD COLUMN IF NOT EXISTS min_adjusted_rating DECIMAL(4, 2) NOT NULL DEFAULT 3.00;

ALTER TABLE place_import_job_items
    ADD COLUMN IF NOT EXISTS region_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS region_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS search_query TEXT,
    ADD COLUMN IF NOT EXISTS review_count INTEGER,
    ADD COLUMN IF NOT EXISTS scraped_review_count INTEGER,
    ADD COLUMN IF NOT EXISTS selected_review_count INTEGER,
    ADD COLUMN IF NOT EXISTS avg_authenticity_score DECIMAL(5, 3),
    ADD COLUMN IF NOT EXISTS place_overall_score DECIMAL(5, 3),
    ADD COLUMN IF NOT EXISTS adjusted_rating DECIMAL(4, 2),
    ADD COLUMN IF NOT EXISTS outcome_reason VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_place_import_items_job_region
    ON place_import_job_items(job_id, region_code, created_at);

CREATE INDEX IF NOT EXISTS idx_place_import_items_job_google_place
    ON place_import_job_items(job_id, google_place_id);

CREATE TABLE IF NOT EXISTS place_import_job_regions (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    region_name VARCHAR(255) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    sequence_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    query_count INTEGER NOT NULL DEFAULT 0,
    discovered_count INTEGER NOT NULL DEFAULT 0,
    processed_count INTEGER NOT NULL DEFAULT 0,
    eligible_count INTEGER NOT NULL DEFAULT 0,
    imported_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_place_import_job_region UNIQUE (job_id, region_code)
);

CREATE INDEX IF NOT EXISTS idx_place_import_job_regions_job_sequence
    ON place_import_job_regions(job_id, sequence_no);

ALTER TABLE places
    ADD COLUMN IF NOT EXISTS score_sample_count INTEGER,
    ADD COLUMN IF NOT EXISTS score_source VARCHAR(64);
