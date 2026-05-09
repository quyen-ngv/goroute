-- Add description field to trips and activities
-- Description is public-facing text shown in UI and when sharing
-- Different from notes which are for internal collaboration

ALTER TABLE trips ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE activities ADD COLUMN IF NOT EXISTS description TEXT;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_trips_description ON trips(description) WHERE description IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_activities_description ON activities(description) WHERE description IS NOT NULL;
