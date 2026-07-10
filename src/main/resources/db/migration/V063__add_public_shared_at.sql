-- Add public_shared_at column to trips table
ALTER TABLE trips 
ADD COLUMN public_shared_at TIMESTAMP;

-- Set public_shared_at to created_at for existing PUBLIC trips
UPDATE trips 
SET public_shared_at = created_at 
WHERE visibility = 'PUBLIC' AND is_deleted = FALSE;

-- Add index for better query performance on public trip searches
CREATE INDEX idx_trips_public_shared_at 
ON trips(public_shared_at) 
WHERE visibility = 'PUBLIC' AND is_deleted = FALSE;
