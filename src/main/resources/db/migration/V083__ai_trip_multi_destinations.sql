ALTER TABLE ai_trip_drafts
    ADD COLUMN IF NOT EXISTS destinations JSONB;

COMMENT ON COLUMN ai_trip_drafts.destinations IS
    'Resolved AI route snapshots: location image, city slug, center coordinates and date range';
