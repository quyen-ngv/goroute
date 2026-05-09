-- Add paid_by_guest_member_id to expenses table
-- This links guest payer to trip_members table (for guest members)

ALTER TABLE expenses
ADD COLUMN paid_by_guest_member_id UUID;

-- Add foreign key constraint to trip_members
ALTER TABLE expenses
ADD CONSTRAINT fk_expenses_paid_by_guest_member
FOREIGN KEY (paid_by_guest_member_id) REFERENCES trip_members(id) ON DELETE SET NULL;
