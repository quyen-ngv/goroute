-- Passport scope rework.
-- A Passport is a collection that may cover several provinces/cities. A tag is always
-- earned from one or more explicitly curated Places; city-wide tag rules are removed.

CREATE TABLE IF NOT EXISTS passport_definition_provinces (
    passport_id  UUID NOT NULL REFERENCES passport_definitions (id) ON DELETE CASCADE,
    province_code VARCHAR(10) NOT NULL REFERENCES provinces (code) ON DELETE RESTRICT,
    PRIMARY KEY (passport_id, province_code)
);
CREATE INDEX IF NOT EXISTS idx_passport_definition_provinces_province
    ON passport_definition_provinces (province_code);

-- Preserve the meaning of legacy city-wide tags as closely as the catalogue data allows:
-- every known Place in the former province becomes an explicit tag Place. New tags may
-- use PASSPORT_PROVINCES when the operator deliberately wants uncatalogued map points
-- in the Passport's provinces to qualify.
INSERT INTO passport_tag_places (passport_tag_id, place_id)
SELECT t.id, p.id
FROM passport_tags t
JOIN places p ON p.province_code = t.province_code
WHERE t.qualification_mode = 'ANY_IN_PROVINCE'
  AND t.province_code IS NOT NULL
ON CONFLICT DO NOTHING;

-- Keep the old columns for rolling deployments and old rows, but make the database reject
-- new city-wide modes. New API clients send only placeIds.
UPDATE passport_tags
SET qualification_mode = 'SPECIFIC_PLACES', province_code = NULL
WHERE qualification_mode = 'ANY_IN_PROVINCE';

ALTER TABLE passport_tags
    DROP CONSTRAINT IF EXISTS ck_passport_tag_city_mode;
ALTER TABLE passport_tags
    DROP CONSTRAINT IF EXISTS ck_passport_tag_mode;
ALTER TABLE passport_tags
    ADD CONSTRAINT ck_passport_tag_mode
        CHECK (qualification_mode = 'SPECIFIC_PLACES');
