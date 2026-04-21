-- Add user_id and is_deleted columns to trip_notes table
ALTER TABLE trip_notes ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE trip_notes ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT false;

-- Drop the old created_by column if it exists (since we're using user_id now)
ALTER TABLE trip_notes DROP COLUMN IF EXISTS created_by;

-- Add index for user_id
CREATE INDEX IF NOT EXISTS idx_trip_notes_user ON trip_notes(user_id);

-- Add comment
COMMENT ON COLUMN trip_notes.user_id IS 'User who created the note';
COMMENT ON COLUMN trip_notes.is_deleted IS 'Soft delete flag';
