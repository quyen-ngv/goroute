CREATE TABLE IF NOT EXISTS place_translations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    locale VARCHAR(16) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    translation_source VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_place_translations_place_locale UNIQUE (place_id, locale)
);

CREATE INDEX IF NOT EXISTS idx_place_translations_locale ON place_translations(locale);
CREATE INDEX IF NOT EXISTS idx_place_translations_place_id ON place_translations(place_id);
CREATE INDEX IF NOT EXISTS idx_place_translations_name_trgm
    ON place_translations USING GIN (name gin_trgm_ops);

INSERT INTO place_translations (place_id, locale, name, description, translation_source, created_at, updated_at)
SELECT id, 'en', title, descriptions, 'LEGACY', created_at, updated_at
FROM places
WHERE title IS NOT NULL
ON CONFLICT (place_id, locale) DO NOTHING;
