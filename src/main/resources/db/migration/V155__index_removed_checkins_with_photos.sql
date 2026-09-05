CREATE INDEX IF NOT EXISTS idx_user_checkins_removed_updated
    ON user_checkins (updated_at, id)
    WHERE is_removed = TRUE;
