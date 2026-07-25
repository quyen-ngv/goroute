-- Persist the route metrics displayed between itinerary timeline stops.
ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS distance_to_next VARCHAR(64),
    ADD COLUMN IF NOT EXISTS duration_to_next VARCHAR(64),
    ADD COLUMN IF NOT EXISTS distance_value_to_next INTEGER,
    ADD COLUMN IF NOT EXISTS duration_value_to_next INTEGER;
