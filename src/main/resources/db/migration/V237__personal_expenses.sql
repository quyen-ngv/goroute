-- Personal expenses (epic 05): an expense outside any trip, seen only by its creator and
-- shown in the Wallet. Such a row has trip_id NULL, paid_by = created_by and one settled
-- split for the creator. Trip expenses are unchanged.

ALTER TABLE expenses ALTER COLUMN trip_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_expenses_personal_owner
    ON expenses (created_by, expense_date DESC)
    WHERE trip_id IS NULL;
