-- Pengoo plans whole trips in chat (27/10/2026). A user sent a photo of a two-day plan and asked
-- "plan it for me"; Pengoo could only describe it and offer the create-trip screen.
--
-- NEW_TRIP: a complete trip (name, destination, dates or a length, budget, starting point, every
--   day's stops). It has no trip until it is applied, so trip_id is empty while pending and holds
--   the created trip afterwards.
-- PLAN_DAYS: the same day-by-day plan poured into an existing trip (an empty one, or its open days).
ALTER TABLE assistant_proposals ALTER COLUMN trip_id DROP NOT NULL;

ALTER TABLE assistant_proposals DROP CONSTRAINT IF EXISTS chk_assistant_proposals_action;
ALTER TABLE assistant_proposals ADD CONSTRAINT chk_assistant_proposals_action
    CHECK (action IN ('ADD', 'MOVE', 'UPDATE_TIME', 'REMOVE', 'REPLACE',
                      'ADD_DAYS', 'PREPEND_DAYS', 'REMOVE_DAYS', 'SWAP_DAYS', 'SHIFT_DATES',
                      'EDIT_TRIP', 'EDIT_ACTIVITY', 'NEW_ACTIVITY', 'ADD_EXPENSE', 'EDIT_EXPENSE',
                      'REMOVE_EXPENSE', 'ADD_NOTE', 'ADD_GUEST', 'NEW_TRIP', 'PLAN_DAYS'));

-- Plans read back into the next question's history, by the message that showed them.
CREATE INDEX IF NOT EXISTS idx_assistant_proposals_message ON assistant_proposals (message_id);
