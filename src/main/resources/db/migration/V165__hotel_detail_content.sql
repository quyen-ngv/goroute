-- Content the guest app needs to render a full stay detail page:
--   * a property gallery, overview facts (opened / renovated year), the tax and
--     service-charge share that is already included in every price, and a list of
--     nearby places the partner or an operator types in by hand;
--   * stay-specific review aspects (location, cleanliness, facilities), next to the
--     existing service rating, so a hotel is not scored on food and ambiance.
-- Structured policies (min check-in age, fees, payment methods) and amenity codes
-- stay inside the existing JSONB columns; their shape is documented on the DTOs.

ALTER TABLE hotel_profiles
    ADD COLUMN IF NOT EXISTS images JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS opened_year SMALLINT,
    ADD COLUMN IF NOT EXISTS renovated_year SMALLINT,
    ADD COLUMN IF NOT EXISTS vat_percent NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS service_charge_percent NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS nearby_places JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE hotel_profiles
    ADD CONSTRAINT chk_hotel_profiles_opened_year
        CHECK (opened_year IS NULL OR opened_year BETWEEN 1800 AND 2100),
    ADD CONSTRAINT chk_hotel_profiles_renovated_year
        CHECK (renovated_year IS NULL OR (renovated_year BETWEEN 1800 AND 2100
               AND (opened_year IS NULL OR renovated_year >= opened_year))),
    ADD CONSTRAINT chk_hotel_profiles_vat_percent
        CHECK (vat_percent IS NULL OR vat_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_hotel_profiles_service_charge_percent
        CHECK (service_charge_percent IS NULL OR service_charge_percent BETWEEN 0 AND 100);

ALTER TABLE user_reviews
    ADD COLUMN IF NOT EXISTS location_rating SMALLINT
        CHECK (location_rating IS NULL OR location_rating BETWEEN 1 AND 5),
    ADD COLUMN IF NOT EXISTS cleanliness_rating SMALLINT
        CHECK (cleanliness_rating IS NULL OR cleanliness_rating BETWEEN 1 AND 5),
    ADD COLUMN IF NOT EXISTS facilities_rating SMALLINT
        CHECK (facilities_rating IS NULL OR facilities_rating BETWEEN 1 AND 5);

ALTER TABLE place_scores
    ADD COLUMN IF NOT EXISTS location_score DECIMAL(2,1)
        CHECK (location_score IS NULL OR location_score BETWEEN 1.0 AND 5.0),
    ADD COLUMN IF NOT EXISTS cleanliness_score DECIMAL(2,1)
        CHECK (cleanliness_score IS NULL OR cleanliness_score BETWEEN 1.0 AND 5.0),
    ADD COLUMN IF NOT EXISTS facilities_score DECIMAL(2,1)
        CHECK (facilities_score IS NULL OR facilities_score BETWEEN 1.0 AND 5.0);
