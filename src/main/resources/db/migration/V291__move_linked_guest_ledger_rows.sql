-- A guest who signed up was linked to their new account (AuthServiceImpl.autoLinkGuestMembers):
-- the trip_members row stayed with is_guest = FALSE and user_id set, and their shares and the
-- legacy payer columns moved to the user. Their expense_payers rows and settlements did not,
-- and the trip ledger reads exactly those. Such a user showed up twice in the balances (owing
-- their shares as the user, owed what they paid as the old guest id). The link now moves both
-- (GuestLedgerMover); this moves the rows earlier links left behind. Same steps as
-- ExpensePayerMapper.xml / SettlementMapper.xml moveGuest*.

-- 1. Payer rows: where the user already paid towards the same expense, merge the amounts.
UPDATE expense_payers u
SET amount = u.amount + g.amount
FROM expense_payers g
JOIN trip_members m ON m.id = g.guest_member_id
WHERE m.is_guest = FALSE
  AND m.user_id IS NOT NULL
  AND u.expense_id = g.expense_id
  AND u.user_id = m.user_id;

DELETE FROM expense_payers g
USING trip_members m
WHERE m.id = g.guest_member_id
  AND m.is_guest = FALSE
  AND m.user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM expense_payers u
              WHERE u.expense_id = g.expense_id AND u.user_id = m.user_id);

UPDATE expense_payers g
SET user_id = m.user_id, guest_member_id = NULL
FROM trip_members m
WHERE m.id = g.guest_member_id
  AND m.is_guest = FALSE
  AND m.user_id IS NOT NULL;

-- 2. Settlements given or received as the guest.
UPDATE settlements s
SET from_user_id = m.user_id, from_guest_member_id = NULL, updated_at = NOW()
FROM trip_members m
WHERE m.id = s.from_guest_member_id
  AND m.is_guest = FALSE
  AND m.user_id IS NOT NULL;

UPDATE settlements s
SET to_user_id = m.user_id, to_guest_member_id = NULL, updated_at = NOW()
FROM trip_members m
WHERE m.id = s.to_guest_member_id
  AND m.is_guest = FALSE
  AND m.user_id IS NOT NULL;

-- 3. A payment between the guest and that same user is now a payment to oneself.
UPDATE settlements
SET deleted_at = NOW(), updated_at = NOW()
WHERE deleted_at IS NULL
  AND from_user_id IS NOT NULL
  AND from_user_id = to_user_id;
