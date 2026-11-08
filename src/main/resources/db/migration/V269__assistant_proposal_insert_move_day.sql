-- Pengoo can insert days in the middle of a trip (INSERT_DAYS) and move one day's plan to another
-- position (MOVE_DAY), the same day changes the itinerary screen offers (7/11/2026).
ALTER TABLE assistant_proposals DROP CONSTRAINT IF EXISTS chk_assistant_proposals_action;
ALTER TABLE assistant_proposals ADD CONSTRAINT chk_assistant_proposals_action
    CHECK (action IN ('ADD', 'MOVE', 'UPDATE_TIME', 'REMOVE', 'REPLACE',
                      'ADD_DAYS', 'PREPEND_DAYS', 'REMOVE_DAYS', 'SWAP_DAYS', 'SHIFT_DATES',
                      'INSERT_DAYS', 'MOVE_DAY',
                      'EDIT_TRIP', 'EDIT_ACTIVITY', 'NEW_ACTIVITY', 'ADD_EXPENSE', 'EDIT_EXPENSE',
                      'REMOVE_EXPENSE', 'ADD_NOTE', 'ADD_GUEST', 'NEW_TRIP', 'PLAN_DAYS'));
