-- Add statistics fields to trips table for tracking engagement

-- Add view count (số lần trip được xem)
ALTER TABLE trips ADD COLUMN IF NOT EXISTS view_count INTEGER DEFAULT 0;

-- Add copy count (số lần trip được clone)
ALTER TABLE trips ADD COLUMN IF NOT EXISTS copy_count INTEGER DEFAULT 0;

-- Add index for sorting by popularity
CREATE INDEX IF NOT EXISTS idx_trips_view_count ON trips (view_count DESC) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_trips_copy_count ON trips (copy_count DESC) WHERE is_deleted = FALSE;

-- Add composite index for public trips sorted by popularity
CREATE INDEX IF NOT EXISTS idx_trips_public_popular 
ON trips (visibility, view_count DESC, copy_count DESC) 
WHERE visibility = 'PUBLIC' AND is_deleted = FALSE;
