-- Ward/province links for the rows that already carried a tourist area but not an
-- administrative one: tours and tickets, hotels, trips and their legs.
--
-- Same shape as the columns V170 put on places and check-ins, and filled the same way:
-- point-in-polygon against wards.geom, or inherited from a parent that already has one.
-- Everything stays NULL until the boundary dataset is loaded and the geo backfill runs,
-- so this migration changes no behaviour on its own.
--
-- food_city_scores is deliberately left out. It scores a food per city and is keyed by
-- city_slug with no coordinate of its own; a ward would have to be invented, and "which
-- ward is bún chả Hà Nội in" is not a question with an answer.

-- Tours, tickets and partner activity products all live in activity_bookings.
-- Caveat worth knowing when reading these columns: search_lat/search_lng is the point the
-- product was found or filed at -- often a city anchor for a scraped tour -- so the
-- province is reliable while the ward is only as precise as that point.
ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS ward_code     VARCHAR(10) REFERENCES wards (code) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS province_code VARCHAR(10);
CREATE INDEX IF NOT EXISTS idx_activity_bookings_ward
    ON activity_bookings (ward_code) WHERE ward_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_activity_bookings_province
    ON activity_bookings (province_code) WHERE province_code IS NOT NULL;

-- Hotels have no coordinates of their own; they inherit from the Place they belong to,
-- exactly as they already inherit their tourist area.
ALTER TABLE hotel_profiles
    ADD COLUMN IF NOT EXISTS ward_code     VARCHAR(10) REFERENCES wards (code) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS province_code VARCHAR(10);
CREATE INDEX IF NOT EXISTS idx_hotel_profiles_ward
    ON hotel_profiles (ward_code) WHERE ward_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_hotel_profiles_province
    ON hotel_profiles (province_code) WHERE province_code IS NOT NULL;

-- A trip's destination coordinate. The ward is over-precise for a multi-day trip; the
-- province is the useful half. Both are stored so a caller can choose.
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS ward_code     VARCHAR(10) REFERENCES wards (code) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS province_code VARCHAR(10);
CREATE INDEX IF NOT EXISTS idx_trips_province
    ON trips (province_code) WHERE province_code IS NOT NULL;

-- One leg of a trip is a real point, so its ward means what it says.
ALTER TABLE trip_destinations
    ADD COLUMN IF NOT EXISTS ward_code     VARCHAR(10) REFERENCES wards (code) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS province_code VARCHAR(10);
CREATE INDEX IF NOT EXISTS idx_trip_destinations_ward
    ON trip_destinations (ward_code) WHERE ward_code IS NOT NULL;
