-- Pengoo may propose changes to everything a trip holds, not only its days and places: the trip's
-- own fields (EDIT_TRIP), an activity's fields (EDIT_ACTIVITY), an activity that is not a
-- catalogue place such as a flight read from a ticket (NEW_ACTIVITY), expenses (ADD_EXPENSE,
-- EDIT_EXPENSE, REMOVE_EXPENSE), shared notes (ADD_NOTE) and guests (ADD_GUEST). A member still
-- applies each one; the payload keeps what applying writes and the before/after lines of the card.
ALTER TABLE assistant_proposals DROP CONSTRAINT IF EXISTS chk_assistant_proposals_action;
ALTER TABLE assistant_proposals ADD CONSTRAINT chk_assistant_proposals_action
    CHECK (action IN ('ADD', 'MOVE', 'UPDATE_TIME', 'REMOVE', 'REPLACE',
                      'ADD_DAYS', 'PREPEND_DAYS', 'REMOVE_DAYS', 'SWAP_DAYS', 'SHIFT_DATES',
                      'EDIT_TRIP', 'EDIT_ACTIVITY', 'NEW_ACTIVITY', 'ADD_EXPENSE', 'EDIT_EXPENSE',
                      'REMOVE_EXPENSE', 'ADD_NOTE', 'ADD_GUEST'));
