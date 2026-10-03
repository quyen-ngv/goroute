-- What a traveller told us when creating a trip (manual or AI): who travels, pace, discovery
-- style, interests, dietary needs and free-text wishes. Stored as typed columns plus one tag
-- table so later analytics/recommendation queries filter with plain indexed equality, e.g.
--   SELECT p.trip_id FROM trip_preferences p
--   JOIN trip_preference_tags t ON t.trip_id = p.trip_id AND t.kind = 'INTEREST' AND t.value = 'FOOD_AND_DRINK'
--   WHERE p.companion = 'FAMILY' AND p.with_children;
-- One row per trip; written once at creation (an AI trip overwrites with source AI).
-- No physical foreign keys: trip_id is a logical reference to trips(id).

CREATE TABLE IF NOT EXISTS trip_preferences (
    trip_id         UUID PRIMARY KEY,
    source          VARCHAR(16)  NOT NULL,
    companion       VARCHAR(16),
    with_children   BOOLEAN      NOT NULL DEFAULT FALSE,
    with_elderly    BOOLEAN      NOT NULL DEFAULT FALSE,
    pace            VARCHAR(16),
    discovery_style VARCHAR(16),
    dietary_note    VARCHAR(120),
    preference_text VARCHAR(2000),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trip_preferences_source CHECK (source IN ('MANUAL', 'AI')),
    CONSTRAINT chk_trip_preferences_companion CHECK (companion IS NULL OR companion IN ('SOLO', 'COUPLE', 'FAMILY', 'FRIENDS')),
    CONSTRAINT chk_trip_preferences_pace CHECK (pace IS NULL OR pace IN ('RELAXED', 'BALANCED', 'EAGER')),
    CONSTRAINT chk_trip_preferences_discovery CHECK (discovery_style IS NULL OR discovery_style IN ('FAMOUS', 'BALANCED', 'HIDDEN_GEMS'))
);

CREATE INDEX IF NOT EXISTS idx_trip_preferences_companion ON trip_preferences (companion, with_children, with_elderly);
CREATE INDEX IF NOT EXISTS idx_trip_preferences_pace ON trip_preferences (pace, discovery_style);

-- Multi-valued answers. kind INTEREST: FOOD_AND_DRINK, CULTURE_AND_HERITAGE, NATURE_AND_OUTDOORS,
-- SHOPPING_AND_MARKET, ATTRACTIONS, ADVENTURE, PHOTOGRAPHY. kind DIETARY: VEGETARIAN, VEGAN, HALAL,
-- NO_PORK, PESCATARIAN, BEEF_FREE, JAIN. Values are upper-case codes, never display text.
CREATE TABLE IF NOT EXISTS trip_preference_tags (
    trip_id UUID        NOT NULL,
    kind    VARCHAR(16) NOT NULL,
    value   VARCHAR(40) NOT NULL,
    PRIMARY KEY (trip_id, kind, value),
    CONSTRAINT chk_trip_preference_tags_kind CHECK (kind IN ('INTEREST', 'DIETARY'))
);

CREATE INDEX IF NOT EXISTS idx_trip_preference_tags_value ON trip_preference_tags (kind, value, trip_id);
