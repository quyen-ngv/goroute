-- Tourist-area ("khu vuc du lich") links.
--
-- Until now the only geographic spine shared by places, checkins and guides was
-- provinces.code, while location_images - the curated tourist areas operators
-- actually publish - had no incoming reference from any operational table. That
-- made "which tourist area does this hotel/tour/place belong to?" unanswerable
-- without re-deriving it from free text on every query.
--
-- Each operational row now carries one primary area. Tours routinely span several
-- areas (a Ha Noi - Ninh Binh - Sapa tour is one product), and a guide covers
-- several, so those two get a link table as well; for tours the primary column
-- stays populated so list screens and indexes never need the join.

-- ---------------------------------------------------------------------------
-- 1. Area coverage radius
-- ---------------------------------------------------------------------------
-- A single global radius cannot serve both Cu Lao Cham (a small island) and
-- Nghe An (a whole province pinned at one point): 30km leaves Ca Mau unmapped
-- while letting Hoi An and Da Nang claim each other's places. Per-area radius is
-- the smallest change that lets an operator fix either case without code.
ALTER TABLE location_images
    ADD COLUMN IF NOT EXISTS coverage_radius_km NUMERIC(6, 2) NOT NULL DEFAULT 30;
ALTER TABLE location_images
    DROP CONSTRAINT IF EXISTS ck_location_images_coverage_radius;
ALTER TABLE location_images
    ADD CONSTRAINT ck_location_images_coverage_radius
        CHECK (coverage_radius_km > 0 AND coverage_radius_km <= 500);

-- ---------------------------------------------------------------------------
-- 2. Resolver used by the backfill below and re-runnable by the admin job
-- ---------------------------------------------------------------------------
-- Nearest area whose own radius actually reaches the point. Returning the
-- nearest rather than the first match matters where two areas overlap: a place
-- in Hoi An is inside Da Nang's radius too, and the closer anchor is the right
-- answer.
CREATE OR REPLACE FUNCTION goroute_nearest_location_image(p_lat NUMERIC, p_lng NUMERIC)
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT candidate.id
    FROM (
        SELECT li.id,
               li.coverage_radius_km,
               6371 * acos(LEAST(1, GREATEST(-1,
                   cos(radians(li.latitude)) * cos(radians(p_lat)) *
                   cos(radians(p_lng) - radians(li.longitude)) +
                   sin(radians(li.latitude)) * sin(radians(p_lat))
               ))) AS distance_km
        FROM location_images li
        WHERE p_lat IS NOT NULL
          AND p_lng IS NOT NULL
          AND li.latitude IS NOT NULL
          AND li.longitude IS NOT NULL
    ) candidate
    WHERE candidate.distance_km <= candidate.coverage_radius_km
    ORDER BY candidate.distance_km
    LIMIT 1
$$;

-- ---------------------------------------------------------------------------
-- 3. Primary area column on every operational table
-- ---------------------------------------------------------------------------
-- SET NULL, not RESTRICT: retiring a curated area must never block on the
-- thousands of rows pointing at it, and an unmapped row is recoverable by
-- re-running the auto-map while a blocked delete is not.
ALTER TABLE places
    ADD COLUMN IF NOT EXISTS location_image_id UUID REFERENCES location_images (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_places_location_image
    ON places (location_image_id) WHERE location_image_id IS NOT NULL;

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS location_image_id UUID REFERENCES location_images (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_trips_location_image
    ON trips (location_image_id) WHERE location_image_id IS NOT NULL;

ALTER TABLE trip_destinations
    ADD COLUMN IF NOT EXISTS location_image_id UUID REFERENCES location_images (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_trip_destinations_location_image
    ON trip_destinations (location_image_id) WHERE location_image_id IS NOT NULL;

ALTER TABLE food_city_scores
    ADD COLUMN IF NOT EXISTS location_image_id UUID REFERENCES location_images (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_food_city_scores_location_image
    ON food_city_scores (location_image_id) WHERE location_image_id IS NOT NULL;

-- activity_bookings backs both the aggregated tour catalogue and the partner
-- "activity product" view, so one column covers both.
ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS location_image_id UUID REFERENCES location_images (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_activity_bookings_location_image
    ON activity_bookings (location_image_id) WHERE location_image_id IS NOT NULL;

ALTER TABLE hotel_profiles
    ADD COLUMN IF NOT EXISTS location_image_id UUID REFERENCES location_images (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_hotel_profiles_location_image
    ON hotel_profiles (location_image_id) WHERE location_image_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. Additional areas for the two multi-area products
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_booking_location_images (
    activity_booking_id UUID NOT NULL REFERENCES activity_bookings (id) ON DELETE CASCADE,
    location_image_id   UUID NOT NULL REFERENCES location_images (id) ON DELETE CASCADE,
    PRIMARY KEY (activity_booking_id, location_image_id)
);
CREATE INDEX IF NOT EXISTS idx_activity_booking_location_images_location
    ON activity_booking_location_images (location_image_id);

-- V140 removed the guide marketplace (guide_profiles, guide_services, bookings) and
-- V151 replaced it with user_guide_grants: a guide is a vouched-for person, not a
-- catalogue row, and the tours they run are activity_bookings which are mapped above.
-- A guide therefore has no coordinates to derive an area from - an operator states
-- which areas the guide covers, and a guide routinely covers several.
CREATE TABLE IF NOT EXISTS user_guide_location_images (
    user_id           UUID NOT NULL REFERENCES user_guide_grants (user_id) ON DELETE CASCADE,
    location_image_id UUID NOT NULL REFERENCES location_images (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, location_image_id)
);
CREATE INDEX IF NOT EXISTS idx_user_guide_location_images_location
    ON user_guide_location_images (location_image_id);

-- ---------------------------------------------------------------------------
-- 5. Backfill by coordinates
-- ---------------------------------------------------------------------------
-- Coordinates only. Name matching needs the same accent/mojibake repair the
-- application already applies (DestinationMatchUtils), so it runs in the admin
-- auto-map job instead of being reimplemented, differently, in SQL here.
UPDATE places
SET location_image_id = goroute_nearest_location_image(latitude, longitude)
WHERE location_image_id IS NULL
  AND latitude IS NOT NULL
  AND longitude IS NOT NULL;

UPDATE trips
SET location_image_id = goroute_nearest_location_image(destination_lat, destination_lng)
WHERE location_image_id IS NULL
  AND destination_lat IS NOT NULL
  AND destination_lng IS NOT NULL;

UPDATE trip_destinations
SET location_image_id = goroute_nearest_location_image(lat, lng)
WHERE location_image_id IS NULL
  AND lat IS NOT NULL
  AND lng IS NOT NULL;

UPDATE activity_bookings
SET location_image_id = goroute_nearest_location_image(search_lat::NUMERIC, search_lng::NUMERIC)
WHERE location_image_id IS NULL
  AND search_lat IS NOT NULL
  AND search_lng IS NOT NULL;

-- A hotel has no coordinates of its own; it is always anchored to a catalogue
-- Place, so inherit that Place's area rather than re-deriving from its address.
UPDATE hotel_profiles hp
SET location_image_id = p.location_image_id
FROM places p
WHERE hp.location_image_id IS NULL
  AND p.id = hp.place_id
  AND p.location_image_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. Backfill foods by city slug
-- ---------------------------------------------------------------------------
-- food_city_scores has no coordinates; it is keyed by the same slug vocabulary
-- location_images already uses.
UPDATE food_city_scores f
SET location_image_id = li.id
FROM location_images li
WHERE f.location_image_id IS NULL
  AND li.city_slug IS NOT NULL
  AND li.city_slug <> ''
  AND li.city_slug = f.city_slug;

-- Only 7 curated areas ever received a city_slug, so slugs such as 'sapa' fall
-- through to the area whose normalized name is the same word.
UPDATE food_city_scores f
SET location_image_id = li.id
FROM location_images li
WHERE f.location_image_id IS NULL
  AND replace(li.normalized_address, ' ', '') = f.city_slug;

-- ---------------------------------------------------------------------------
-- 7. Seed the multi-area tables from the primary area
-- ---------------------------------------------------------------------------
INSERT INTO activity_booking_location_images (activity_booking_id, location_image_id)
SELECT id, location_image_id
FROM activity_bookings
WHERE location_image_id IS NOT NULL
ON CONFLICT DO NOTHING;
