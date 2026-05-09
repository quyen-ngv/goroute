-- Add spatial index for trips destination coordinates to optimize location-based search
-- Using btree index with lat/lng for Haversine distance calculation

CREATE INDEX IF NOT EXISTS idx_trips_destination_coords 
ON trips (destination_lat, destination_lng) 
WHERE destination_lat IS NOT NULL 
  AND destination_lng IS NOT NULL 
  AND is_deleted = FALSE;

-- Add index for visibility to optimize public trip queries
CREATE INDEX IF NOT EXISTS idx_trips_visibility 
ON trips (visibility) 
WHERE is_deleted = FALSE;

-- Add composite index for public trip search
CREATE INDEX IF NOT EXISTS idx_trips_public_search 
ON trips (visibility, destination_lat, destination_lng, destination) 
WHERE visibility = 'PUBLIC' 
  AND is_deleted = FALSE 
  AND destination_lat IS NOT NULL 
  AND destination_lng IS NOT NULL;
