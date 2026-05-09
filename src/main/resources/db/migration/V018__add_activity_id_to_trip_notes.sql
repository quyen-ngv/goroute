-- Add activity_id column to trip_notes table
ALTER TABLE trip_notes ADD COLUMN activity_id UUID;

-- Add index for activity_id
CREATE INDEX idx_trip_notes_activity_id ON trip_notes(activity_id);

-- Add comment
COMMENT ON COLUMN trip_notes.activity_id IS 'Optional reference to activity for activity-specific notes';
