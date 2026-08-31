-- Two additions, both purely additive: no existing column changes meaning, and every
-- new column is nullable, so a client that knows nothing about them keeps working.

-- 1. Where a memory was taken.
--
-- media_assets already carried coordinates (V136), which is enough to put a photo on a
-- map but not enough to say where it was: a reader wants "Cà phê Giảng", not
-- 21.0339, 105.8524. `place_id` is set when the author picked a catalogued place;
-- `location_name` is what to show whether or not there was one, because a map result or
-- a reverse-geocoded current position has a name and no catalogue row.
ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS place_id UUID REFERENCES places (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS location_name VARCHAR(255),
    -- CATALOGUE_PLACE | TRIP_ACTIVITY | MAP_SEARCH | REVERSE_GEOCODE | USER_NAMED |
    -- COORDINATES_ONLY -- the same vocabulary check-ins use, so the two agree.
    ADD COLUMN IF NOT EXISTS location_source VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_media_assets_place
    ON media_assets (place_id)
    WHERE place_id IS NOT NULL AND deleted_at IS NULL;

-- 2. Words on a single check-in photo.
--
-- A check-in already has one caption for the visit. These are per photo, for the reader
-- swiping through them: which dish, which room, which view. Null for every existing row
-- and for anybody who does not fill them in.
ALTER TABLE user_checkin_photos
    ADD COLUMN IF NOT EXISTS title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS description TEXT;
