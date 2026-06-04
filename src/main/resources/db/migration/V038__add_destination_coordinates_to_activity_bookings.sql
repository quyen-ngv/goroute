ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS destination_coordinates JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN activity_bookings.destination_coordinates IS
    'List of {lat, lng} points defining activity coverage for geo search';
