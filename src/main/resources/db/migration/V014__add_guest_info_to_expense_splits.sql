-- Add guest member info to expense_splits
-- This allows expense splits to directly store guest information

ALTER TABLE expense_splits
ADD COLUMN guest_name VARCHAR(255),
ADD COLUMN guest_email VARCHAR(255);

-- Add check constraint: either user_id or guest_name must be present
ALTER TABLE expense_splits
ADD CONSTRAINT chk_expense_split_member CHECK (
    (user_id IS NOT NULL) OR 
    (guest_name IS NOT NULL)
);

-- Add index for guest email lookup
CREATE INDEX idx_expense_splits_guest_email ON expense_splits(guest_email) WHERE guest_email IS NOT NULL;
