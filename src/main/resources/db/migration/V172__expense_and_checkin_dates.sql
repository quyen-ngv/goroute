-- When something happened, as opposed to when it was typed in.
--
-- People book a trip, go on it, and only open the app once they are home. Until now both
-- an expense and a check-in were stamped with the moment the row was written, so a week of
-- travel collapsed onto the evening it was entered: the expense list showed one day, and
-- the history map put every visit on the day of the upload.

-- The day the money was actually spent. Backfilled from created_at so every existing row
-- keeps the date it has always been displayed with.
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS expense_date TIMESTAMP;
UPDATE expenses SET expense_date = created_at WHERE expense_date IS NULL;
ALTER TABLE expenses ALTER COLUMN expense_date SET DEFAULT NOW();
ALTER TABLE expenses ALTER COLUMN expense_date SET NOT NULL;

-- The trip expense list orders by this and nothing else.
CREATE INDEX IF NOT EXISTS idx_expenses_trip_expense_date
    ON expenses (trip_id, expense_date DESC);

-- The day of the visit. created_at stays untouched next to it: it is what the reward
-- window, the moderation queue and the feed cursor count on, and a date the author chooses
-- must never be able to move any of those.
ALTER TABLE user_checkins ADD COLUMN IF NOT EXISTS visited_at TIMESTAMP;
UPDATE user_checkins SET visited_at = created_at WHERE visited_at IS NULL;
ALTER TABLE user_checkins ALTER COLUMN visited_at SET DEFAULT NOW();
ALTER TABLE user_checkins ALTER COLUMN visited_at SET NOT NULL;

-- Profile history and the visited-wards map both read the author's own check-ins by visit date.
CREATE INDEX IF NOT EXISTS idx_user_checkins_user_visited_at
    ON user_checkins (user_id, visited_at DESC);
