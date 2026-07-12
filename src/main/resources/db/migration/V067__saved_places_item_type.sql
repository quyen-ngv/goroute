ALTER TABLE saved_places
    ADD COLUMN IF NOT EXISTS item_type VARCHAR(30) NOT NULL DEFAULT 'PLACE';

UPDATE saved_places
SET item_type = 'PLACE'
WHERE item_type IS NULL OR item_type = '';

ALTER TABLE saved_places
    DROP CONSTRAINT IF EXISTS saved_places_user_id_place_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_places_user_item_type_place
    ON saved_places(user_id, item_type, place_id)
    WHERE place_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_saved_places_user_item_type
    ON saved_places(user_id, item_type, created_at DESC);
