-- expense_splits has been read by expense_id on every expense list, budget overview and
-- Wallet screen since V001, but nothing has indexed that column since V017 dropped the
-- UNIQUE (expense_id, user_id) constraint that used to cover it: every one of those reads
-- has been a sequential scan of the whole table. idx_expense_splits_user (user_id,
-- is_settled) and idx_expense_splits_guest_member_id do not help an expense_id lookup.
--
-- It matters more now that ExpenseServiceImpl reads a page of splits with one
-- expense_id IN (...) statement instead of one statement per expense, and the Wallet
-- queries join expense_splits to the accessible-expense set.
CREATE INDEX IF NOT EXISTS idx_expense_splits_expense
    ON expense_splits (expense_id);
