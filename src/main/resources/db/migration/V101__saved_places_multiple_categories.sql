DROP INDEX IF EXISTS idx_saved_places_user_item_type_place;

CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_places_user_item_type_place_category
    ON saved_places (
        user_id,
        item_type,
        place_id,
        COALESCE(NULLIF(BTRIM(category), ''), '')
    )
    WHERE place_id IS NOT NULL;
