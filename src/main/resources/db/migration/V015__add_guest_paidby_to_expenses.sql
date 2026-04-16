-- Add guest payer info to expenses
-- This allows expenses to be paid by guest members

ALTER TABLE expenses
ADD COLUMN paid_by_guest_name VARCHAR(255),
ADD COLUMN paid_by_guest_email VARCHAR(255);

-- Add check constraint: either paid_by or paid_by_guest_name must be present
ALTER TABLE expenses
ADD CONSTRAINT chk_expense_payer CHECK (
    (paid_by IS NOT NULL) OR 
    (paid_by_guest_name IS NOT NULL)
);
