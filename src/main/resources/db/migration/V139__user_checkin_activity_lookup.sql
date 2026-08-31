-- "Have I already checked in at this activity?" asked on every trip screen that offers
-- a check-in, so it gets an index rather than a scan of the author's history.
--
-- Partial: only check-ins attached to an activity can answer it, and most are not.
CREATE INDEX IF NOT EXISTS idx_user_checkins_user_activity
    ON user_checkins (user_id, activity_id, created_at DESC)
    WHERE activity_id IS NOT NULL AND is_removed = FALSE;
