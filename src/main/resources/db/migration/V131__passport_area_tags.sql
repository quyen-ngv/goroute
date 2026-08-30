-- Allow a Passport tag to be province/city-wide when no explicit Places are curated.
-- SPECIFIC_PLACES remains the default for existing rows; the service derives the mode
-- from the selected Place list and rejects inconsistent payloads.
ALTER TABLE passport_tags
    DROP CONSTRAINT IF EXISTS ck_passport_tag_mode;

ALTER TABLE passport_tags
    ADD CONSTRAINT ck_passport_tag_mode
        CHECK (qualification_mode IN ('SPECIFIC_PLACES', 'PASSPORT_PROVINCES'));
