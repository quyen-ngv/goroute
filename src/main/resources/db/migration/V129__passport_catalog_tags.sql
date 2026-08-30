-- Passport catalog: a passport is a curated collection, a tag is the proof a traveller
-- earns.  They deliberately live outside places and location images: one city image or
-- one place can participate in several tags, and a future tier/rule can be added without
-- changing catalogue content or rewriting awards already granted to travellers.

ALTER TABLE location_images
    ADD COLUMN IF NOT EXISTS province_code VARCHAR(10)
        REFERENCES provinces (code) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_location_images_passport_province
    ON location_images (province_code) WHERE province_code IS NOT NULL;

CREATE TABLE passport_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(80) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    cover_image_url TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE passport_tags (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passport_id            UUID NOT NULL REFERENCES passport_definitions (id) ON DELETE RESTRICT,
    code                   VARCHAR(80) NOT NULL UNIQUE,
    name                   VARCHAR(200) NOT NULL,
    description            TEXT,
    image_url              TEXT,
    -- A city/province remains the default scope so a valid raw map check-in can count
    -- before every spot is curated. SPECIFIC_PLACES is ready for city gates such as
    -- "Hồ Gươm counts as Hà Nội" when Product chooses to tighten a tag.
    province_code          VARCHAR(10) REFERENCES provinces (code) ON DELETE SET NULL,
    qualification_mode     VARCHAR(30) NOT NULL DEFAULT 'ANY_IN_PROVINCE',
    required_checkin_count INT NOT NULL DEFAULT 1,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    display_order          INT NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_passport_tag_mode CHECK (qualification_mode IN ('ANY_IN_PROVINCE', 'SPECIFIC_PLACES')),
    CONSTRAINT ck_passport_tag_required_checkins CHECK (required_checkin_count >= 1),
    CONSTRAINT ck_passport_tag_city_mode CHECK (
        qualification_mode <> 'ANY_IN_PROVINCE' OR province_code IS NOT NULL
    )
);
CREATE INDEX idx_passport_tags_province ON passport_tags (province_code)
    WHERE is_active = TRUE AND qualification_mode = 'ANY_IN_PROVINCE';

-- LocationImage anchors a city-wide tag to the city/attraction card that operators
-- configure today. Place mappings enable a later, precise subset of catalogue places
-- without adding another relationship shape.
CREATE TABLE passport_tag_location_images (
    passport_tag_id   UUID NOT NULL REFERENCES passport_tags (id) ON DELETE CASCADE,
    location_image_id UUID NOT NULL REFERENCES location_images (id) ON DELETE CASCADE,
    PRIMARY KEY (passport_tag_id, location_image_id)
);

CREATE TABLE passport_tag_places (
    passport_tag_id UUID NOT NULL REFERENCES passport_tags (id) ON DELETE CASCADE,
    place_id        UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    PRIMARY KEY (passport_tag_id, place_id)
);
CREATE INDEX idx_passport_tag_places_place ON passport_tag_places (place_id);

-- An earned tag is immutable evidence. The uniqueness is the idempotency boundary for
-- concurrent/replayed check-in consequences; changing a tag later can affect only future
-- travellers, never remove a badge already given.
CREATE TABLE user_passport_tags (
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    passport_tag_id    UUID NOT NULL REFERENCES passport_tags (id) ON DELETE RESTRICT,
    triggering_event_id UUID REFERENCES passport_events (id) ON DELETE SET NULL,
    awarded_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, passport_tag_id)
);
CREATE INDEX idx_user_passport_tags_user ON user_passport_tags (user_id, awarded_at DESC);

-- Passport configuration is a separate operator capability from reward/voucher handling.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('passport-catalog', 'get'),
    ('passport-catalog', 'create'),
    ('passport-catalog', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'passport-catalog'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
