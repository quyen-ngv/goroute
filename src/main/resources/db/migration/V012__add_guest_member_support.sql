-- Add guest member support to trip_members table
ALTER TABLE trip_members
ADD COLUMN guest_name VARCHAR(255),
ADD COLUMN guest_email VARCHAR(255),
ADD COLUMN guest_phone VARCHAR(50),
ADD COLUMN is_guest BOOLEAN DEFAULT FALSE;

-- Make user_id nullable to support guest members
ALTER TABLE trip_members
ALTER COLUMN user_id DROP NOT NULL;

-- Add index for guest email lookup (for linking later)
CREATE INDEX idx_trip_members_guest_email ON trip_members(guest_email) WHERE guest_email IS NOT NULL;

-- Add check constraint: either user_id or guest_name must be present
ALTER TABLE trip_members
ADD CONSTRAINT chk_member_type CHECK (
    (user_id IS NOT NULL AND is_guest = FALSE) OR 
    (guest_name IS NOT NULL AND is_guest = TRUE)
);
