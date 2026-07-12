CREATE TABLE IF NOT EXISTS trip_destinations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    place_id VARCHAR(255),
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    order_index INT NOT NULL DEFAULT 0,
    start_date DATE,
    end_date DATE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trip_destinations_trip_order
ON trip_destinations (trip_id, order_index);

CREATE INDEX IF NOT EXISTS idx_trip_destinations_coords
ON trip_destinations (lat, lng)
WHERE lat IS NOT NULL AND lng IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trip_destinations_trip_dates
ON trip_destinations (trip_id, start_date, end_date);

INSERT INTO trip_destinations (
    id, trip_id, name, address, place_id, lat, lng, order_index,
    start_date, end_date, is_primary, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    t.id,
    COALESCE(NULLIF(t.destination, ''), t.name),
    t.destination,
    t.destination_place_id,
    t.destination_lat,
    t.destination_lng,
    0,
    t.start_date,
    t.end_date,
    TRUE,
    NOW(),
    NOW()
FROM trips t
WHERE t.is_deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM trip_destinations td WHERE td.trip_id = t.id
  )
  AND (
      t.destination IS NOT NULL
      OR t.destination_lat IS NOT NULL
      OR t.destination_lng IS NOT NULL
  );
