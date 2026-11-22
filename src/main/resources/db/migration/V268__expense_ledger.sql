-- Splitwise-style ledger (epic 05, plan .docs/SPLIT_MONEY_SPLITWISE_PLAN_2026-11-05.md §2, §7, §8).
--
-- Until now the only record of "who still owes what" was expense_splits.is_settled, one flag
-- per share, and every expense had exactly one payer. From here a balance is computed, never
-- stored: what a person paid (expense_payers), minus what they owe (expense_splits.amount),
-- plus what they handed to someone else, minus what they received (settlements). is_settled
-- stays for app builds already on the store; the ledger does not read it.
--
-- Every participant column is a pair (user_id, guest_member_id) with exactly one set: a
-- guest is a trip_members row with is_guest = TRUE. No foreign key on either half, as in the
-- rest of the schema: a guest row is deleted when it is linked to an account, and the link
-- moves every row first (TripServiceImpl.linkGuestToUser).

-- 1. Several payers per expense. expenses.paid_by stays, as the largest payer, for old builds.
CREATE TABLE IF NOT EXISTS expense_payers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id      UUID NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id         UUID,
    guest_member_id UUID,
    amount          DECIMAL(15, 2) NOT NULL,
    position        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_expense_payers_one_participant CHECK ((user_id IS NULL) <> (guest_member_id IS NULL)),
    CONSTRAINT chk_expense_payers_amount CHECK (amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_expense_payers_expense ON expense_payers (expense_id, position);
CREATE INDEX IF NOT EXISTS idx_expense_payers_user ON expense_payers (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_expense_payers_guest ON expense_payers (guest_member_id) WHERE guest_member_id IS NOT NULL;

-- Old builds sent a guest's trip_members id in paidBy when the guest was not in the split
-- (plan §1 bug 6). Such a row has paid_by = a guest member id and paid_by_guest_member_id
-- NULL, which every query read as an unknown user. Mark it as the guest payer it was.
UPDATE expenses e
SET paid_by_guest_member_id = gm.id,
    paid_by_guest_name = gm.guest_name
FROM trip_members gm
WHERE gm.id = e.paid_by
  AND gm.trip_id = e.trip_id
  AND gm.is_guest = TRUE
  AND e.paid_by_guest_member_id IS NULL;

-- One payer row per existing expense, for the whole amount.
INSERT INTO expense_payers (id, expense_id, user_id, guest_member_id, amount, position, created_at)
SELECT gen_random_uuid(),
       e.id,
       CASE WHEN e.paid_by_guest_member_id IS NULL THEN e.paid_by END,
       e.paid_by_guest_member_id,
       e.amount,
       0,
       COALESCE(e.created_at, NOW())
FROM expenses e
WHERE e.paid_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM expense_payers p WHERE p.expense_id = e.id);

-- 2. How a share was asked for: a percentage, a number of shares or a +/- adjustment.
--    expense_splits.amount stays the final amount the person bears.
ALTER TABLE expense_splits ADD COLUMN IF NOT EXISTS share_input DECIMAL(19, 6);

-- Guest splits written without guest_member_id carry only a name. Link the ones whose name
-- matches exactly one guest of the same trip; the rest stay unlinked and the ledger leaves
-- their share with the payer.
UPDATE expense_splits es
SET guest_member_id = matched.member_id
FROM (
    SELECT candidate.id AS split_id,
           (ARRAY_AGG(tm.id))[1] AS member_id,
           COUNT(*) AS matches
    FROM expense_splits candidate
    JOIN expenses e ON e.id = candidate.expense_id
    JOIN trip_members tm ON tm.trip_id = e.trip_id
                        AND tm.is_guest = TRUE
                        AND LOWER(TRIM(tm.guest_name)) = LOWER(TRIM(candidate.guest_name))
    WHERE candidate.user_id IS NULL
      AND candidate.guest_member_id IS NULL
      AND candidate.guest_name IS NOT NULL
    GROUP BY candidate.id
) matched
WHERE es.id = matched.split_id
  AND matched.matches = 1;

-- 3. How the expense was split. NULL is a row written before this migration (or by an old
--    build): the response then reads EQUAL when the shares are the even split, else EXACT.
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS split_method VARCHAR(20);
ALTER TABLE expenses DROP CONSTRAINT IF EXISTS chk_expenses_split_method;
ALTER TABLE expenses ADD CONSTRAINT chk_expenses_split_method
    CHECK (split_method IS NULL OR split_method IN ('EQUAL', 'EXACT', 'PERCENT', 'SHARES', 'ADJUSTMENT'));

-- 4. Money handed from one person to another. A partial payment is a smaller settlement; a
--    delete is soft so it can be undone. Only trips have a ledger; expenses outside trips keep
--    the old per-share flag.
--    split_id links the settlement an old build's "mark as paid" wrote for one share.
CREATE TABLE IF NOT EXISTS settlements (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id              UUID NOT NULL,
    from_user_id         UUID,
    from_guest_member_id UUID,
    to_user_id           UUID,
    to_guest_member_id   UUID,
    amount               DECIMAL(15, 2) NOT NULL,
    currency             VARCHAR(10) NOT NULL,
    method               VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    note                 VARCHAR(500),
    paid_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    original_amount      DECIMAL(15, 2),
    original_currency    VARCHAR(10),
    split_id             UUID REFERENCES expense_splits (id) ON DELETE SET NULL,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMP,
    CONSTRAINT chk_settlements_from CHECK ((from_user_id IS NULL) <> (from_guest_member_id IS NULL)),
    CONSTRAINT chk_settlements_to CHECK ((to_user_id IS NULL) <> (to_guest_member_id IS NULL)),
    CONSTRAINT chk_settlements_amount CHECK (amount > 0),
    CONSTRAINT chk_settlements_original
        CHECK ((original_amount IS NULL) = (original_currency IS NULL)),
    CONSTRAINT chk_settlements_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'E_WALLET', 'OTHER'))
);
CREATE INDEX IF NOT EXISTS idx_settlements_trip ON settlements (trip_id, paid_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_settlements_from_user ON settlements (from_user_id) WHERE from_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_settlements_to_user ON settlements (to_user_id) WHERE to_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_settlements_from_guest
    ON settlements (from_guest_member_id) WHERE from_guest_member_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_settlements_to_guest
    ON settlements (to_guest_member_id) WHERE to_guest_member_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_settlements_split ON settlements (split_id) WHERE split_id IS NOT NULL;

-- Every share already marked as paid by someone other than its payer becomes the payment it
-- stood for, so the new balances say exactly what the old flags said. A payer's own share
-- needs nothing: it never was a debt.
INSERT INTO settlements (id, trip_id, from_user_id, from_guest_member_id, to_user_id, to_guest_member_id,
                         amount, currency, method, note, paid_at, split_id, created_by, created_at, updated_at)
SELECT gen_random_uuid(),
       e.trip_id,
       es.user_id,
       CASE WHEN es.user_id IS NULL THEN es.guest_member_id END,
       payer.user_id,
       payer.guest_member_id,
       es.amount,
       e.currency,
       'OTHER',
       'Đã đánh dấu đã trả',
       COALESCE(es.settled_at, e.created_at, NOW()),
       es.id,
       e.created_by,
       NOW(),
       NOW()
FROM expense_splits es
JOIN expenses e ON e.id = es.expense_id
JOIN expense_payers payer ON payer.expense_id = e.id
WHERE es.is_settled = TRUE
  AND es.amount > 0
  AND (es.user_id IS NOT NULL OR es.guest_member_id IS NOT NULL)
  AND CASE WHEN es.user_id IS NOT NULL THEN es.user_id IS DISTINCT FROM payer.user_id
           ELSE es.guest_member_id IS DISTINCT FROM payer.guest_member_id END
  AND e.trip_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM settlements s WHERE s.split_id = es.id);

-- 5. Debt simplification, on by default (plan §7.2); owner or editor turns it off per trip.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS simplify_debts BOOLEAN NOT NULL DEFAULT TRUE;
