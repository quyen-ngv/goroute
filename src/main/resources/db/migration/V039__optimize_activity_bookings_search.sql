-- Denormalized fields for fast geo + destination search (avoid jsonb haversine full scan)
ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS search_lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS search_lng DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS destinations_norm TEXT;

UPDATE activity_bookings
SET
    search_lat = ((destination_coordinates->0)->>'lat')::double precision,
    search_lng = ((destination_coordinates->0)->>'lng')::double precision
WHERE jsonb_typeof(destination_coordinates) = 'array'
  AND jsonb_array_length(destination_coordinates) > 0;

UPDATE activity_bookings ab
SET destinations_norm = sub.norm
FROM (
    SELECT
        ab2.id,
        string_agg(
            regexp_replace(
                lower(translate(trim(elem.value), 'đĐ', 'dD')),
                '[^a-z0-9]',
                '',
                'g'
            ),
            '|'
        ) AS norm
    FROM activity_bookings ab2
    CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ab2.destinations, '[]'::jsonb)) AS elem(value)
    WHERE trim(elem.value) <> ''
    GROUP BY ab2.id
) AS sub
WHERE ab.id = sub.id;

CREATE INDEX IF NOT EXISTS idx_activity_bookings_search_lat_lng
    ON activity_bookings (search_lat, search_lng)
    WHERE search_lat IS NOT NULL AND search_lng IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_activity_bookings_destinations_norm_trgm
    ON activity_bookings USING gin (destinations_norm gin_trgm_ops)
    WHERE destinations_norm IS NOT NULL AND destinations_norm <> '';
