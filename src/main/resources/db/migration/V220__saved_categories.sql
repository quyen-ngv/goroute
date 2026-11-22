-- Saved categories become records of their own, so a category can be renamed, deleted,
-- given a cover photo and shown on the owner's profile.
--
-- saved_places keeps naming its category by text (the save API and every client send the
-- name). A row belongs to the category whose name matches LOWER(BTRIM(category)): the app
-- already reuses an existing name regardless of case, and older rows that differ only in
-- case are one category, not two.
CREATE TABLE IF NOT EXISTS saved_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    cover_url VARCHAR(1000),
    -- PRIVATE: only the owner. PUBLIC: anyone signed in, on the owner's profile.
    -- A followers-only level is planned; widen the CHECK when it lands.
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'
        CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_categories_user_name
    ON saved_categories (user_id, LOWER(BTRIM(name)));

CREATE INDEX IF NOT EXISTS idx_saved_categories_user_visibility
    ON saved_categories (user_id, visibility);

-- The category key every per-category read, rename and delete filters on.
CREATE INDEX IF NOT EXISTS idx_saved_places_user_category_key
    ON saved_places (user_id, LOWER(BTRIM(category)))
    WHERE category IS NOT NULL;

-- One record per category that already exists, named after its earliest spelling.
INSERT INTO saved_categories (user_id, name, created_at, updated_at)
SELECT user_id,
       (ARRAY_AGG(BTRIM(category) ORDER BY created_at NULLS LAST, id))[1],
       COALESCE(MIN(created_at), NOW()),
       NOW()
FROM saved_places
WHERE category IS NOT NULL
  AND BTRIM(category) <> ''
GROUP BY user_id, LOWER(BTRIM(category))
ON CONFLICT DO NOTHING;
