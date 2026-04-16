-- Allow guest members in expense splits
-- Make user_id nullable to support guest members in expense splitting

ALTER TABLE expense_splits
ALTER COLUMN user_id DROP NOT NULL;

-- Drop old unique constraint (expense_id, user_id)
ALTER TABLE expense_splits
DROP CONSTRAINT IF EXISTS expense_splits_expense_id_user_id_key;

-- Note: We can't add a unique constraint that works for both registered users and guests
-- because multiple guests (user_id = null) would violate the constraint.
-- The application layer will handle duplicate prevention.
