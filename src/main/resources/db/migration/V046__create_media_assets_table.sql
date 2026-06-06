CREATE TABLE IF NOT EXISTS media_assets (
    id UUID PRIMARY KEY,
    trip_id UUID,
    activity_id UUID,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    url TEXT NOT NULL,
    caption TEXT,
    uploaded_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_media_assets_trip
    ON media_assets(trip_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_media_assets_activity
    ON media_assets(activity_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_media_assets_entity
    ON media_assets(entity_type, entity_id)
    WHERE deleted_at IS NULL;
