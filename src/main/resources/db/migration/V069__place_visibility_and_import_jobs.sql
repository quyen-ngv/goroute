ALTER TABLE places
    ADD COLUMN IF NOT EXISTS visibility_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_places_visibility_status ON places(visibility_status);
CREATE INDEX IF NOT EXISTS idx_places_cid ON places(cid);
CREATE INDEX IF NOT EXISTS idx_places_lat_lng ON places(latitude, longitude);

ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS place_ref_id UUID;

CREATE INDEX IF NOT EXISTS idx_activities_place_ref_id ON activities(place_ref_id);
