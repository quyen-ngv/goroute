-- Passport collections are anchored to curated Location Images.  Province columns are
-- retained for rolling compatibility with older catalogue rows, but new configuration
-- resolves its default Place scope from these coordinates.
CREATE TABLE IF NOT EXISTS passport_definition_location_images (
    passport_id       UUID NOT NULL REFERENCES passport_definitions (id) ON DELETE CASCADE,
    location_image_id UUID NOT NULL REFERENCES location_images (id) ON DELETE RESTRICT,
    PRIMARY KEY (passport_id, location_image_id)
);
CREATE INDEX IF NOT EXISTS idx_passport_definition_location_images_location
    ON passport_definition_location_images (location_image_id);

-- Keep the event coordinates so a map check-in (without a catalogue Place) can still be
-- tested against the Passport's Location Image radius during tag evaluation.
ALTER TABLE passport_events
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);
CREATE INDEX IF NOT EXISTS idx_passport_events_coordinates
    ON passport_events (latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

ALTER TABLE passport_tags
    DROP CONSTRAINT IF EXISTS ck_passport_tag_mode;
ALTER TABLE passport_tags
    ADD CONSTRAINT ck_passport_tag_mode
        CHECK (qualification_mode IN ('SPECIFIC_PLACES', 'PASSPORT_PROVINCES', 'PASSPORT_LOCATIONS'));

INSERT INTO config (label, key, value, description, is_active)
VALUES ('PASSPORT', 'LOCATION_PLACE_RADIUS_KM', '5',
        'Ban kinh Place mac dinh quanh Location Image cua Passport (km, 0.1..50)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
