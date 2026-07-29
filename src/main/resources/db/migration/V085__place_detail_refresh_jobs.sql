CREATE UNIQUE INDEX IF NOT EXISTS idx_place_import_jobs_active_detail_refresh
    ON place_import_jobs(created_at DESC)
    WHERE source_type = 'PLACE_DETAILS_REFRESH' AND status IN ('QUEUED', 'PROCESSING');
