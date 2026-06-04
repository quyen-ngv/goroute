-- Support multi-destination filtering for places and activity bookings

ALTER TABLE places
    ADD COLUMN IF NOT EXISTS destinations JSONB DEFAULT '[]'::jsonb;

UPDATE places
SET destinations = '[]'::jsonb
WHERE destinations IS NULL;

CREATE INDEX IF NOT EXISTS idx_places_destinations_gin
    ON places USING GIN(destinations);

ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS destinations JSONB DEFAULT '[]'::jsonb;

UPDATE activity_bookings
SET destinations = COALESCE(navigation_list, '[]'::jsonb)
WHERE destinations IS NULL;

CREATE INDEX IF NOT EXISTS idx_activity_bookings_destinations_gin
    ON activity_bookings USING GIN(destinations);
