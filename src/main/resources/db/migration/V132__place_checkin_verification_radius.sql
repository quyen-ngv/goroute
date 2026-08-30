-- A nullable override lets large or irregular catalogue places use a wider geofence
-- without changing the global policy. NULL deliberately means "use config".
ALTER TABLE places
    ADD COLUMN IF NOT EXISTS verification_radius_meters INTEGER;

ALTER TABLE places
    DROP CONSTRAINT IF EXISTS ck_places_verification_radius_meters;

ALTER TABLE places
    ADD CONSTRAINT ck_places_verification_radius_meters
        CHECK (verification_radius_meters IS NULL
            OR (verification_radius_meters BETWEEN 20 AND 5000));
