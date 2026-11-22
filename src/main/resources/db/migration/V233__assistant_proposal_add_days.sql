-- Pengoo may propose changes to the trip's days, not only to its activities: lengthen it at the
-- end (ADD_DAYS) or the start (PREPEND_DAYS), remove days (REMOVE_DAYS), swap two days
-- (SWAP_DAYS), move the whole trip (SHIFT_DATES); and put another place in an activity (REPLACE).
-- "Add one more day" now extends the trip the chat belongs to instead of sending the group off
-- to create a new trip.
ALTER TABLE assistant_proposals DROP CONSTRAINT IF EXISTS chk_assistant_proposals_action;
ALTER TABLE assistant_proposals ADD CONSTRAINT chk_assistant_proposals_action
    CHECK (action IN ('ADD', 'MOVE', 'UPDATE_TIME', 'REMOVE', 'REPLACE',
                      'ADD_DAYS', 'PREPEND_DAYS', 'REMOVE_DAYS', 'SWAP_DAYS', 'SHIFT_DATES'));
