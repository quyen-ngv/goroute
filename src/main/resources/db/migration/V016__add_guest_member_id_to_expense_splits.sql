-- Add guest_member_id to expense_splits to link with trip_members
-- This allows proper tracking when linking guest to real user

ALTER TABLE expense_splits
ADD COLUMN guest_member_id UUID;

-- Add index for guest_member_id lookup
CREATE INDEX idx_expense_splits_guest_member_id ON expense_splits(guest_member_id) WHERE guest_member_id IS NOT NULL;

-- Add comment
COMMENT ON COLUMN expense_splits.guest_member_id IS 'Reference to trip_members.id when split belongs to a guest member';
