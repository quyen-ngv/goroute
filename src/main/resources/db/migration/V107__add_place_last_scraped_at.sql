ALTER TABLE places
    ADD COLUMN IF NOT EXISTS last_scraped_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_places_active_last_scraped_at
    ON places(last_scraped_at ASC NULLS FIRST)
    WHERE visibility_status = 'ACTIVE';
