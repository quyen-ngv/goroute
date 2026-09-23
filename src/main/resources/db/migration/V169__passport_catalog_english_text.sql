-- English copy for the Passport catalogue. The existing name/description columns stay the
-- Vietnamese source text; a blank English value falls back to it when served.
ALTER TABLE passport_definitions
    ADD COLUMN IF NOT EXISTS name_en        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS description_en TEXT;

ALTER TABLE passport_tags
    ADD COLUMN IF NOT EXISTS name_en        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS description_en TEXT;

ALTER TABLE passport_stamp_rules
    ADD COLUMN IF NOT EXISTS name_en        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS description_en TEXT;

ALTER TABLE passport_rewards
    ADD COLUMN IF NOT EXISTS name_en        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS description_en TEXT;
