-- Remove UNIQUE constraint on (expense_id, user_id) to allow same user multiple splits in one expense
-- This is needed for guest-to-user linking where both guest and user may have splits in same expense

ALTER TABLE expense_splits DROP CONSTRAINT IF EXISTS expense_splits_expense_id_user_id_key;
