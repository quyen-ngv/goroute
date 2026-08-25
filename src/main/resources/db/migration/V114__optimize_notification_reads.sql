CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_trip_created
    ON notifications (user_id, trip_id, created_at DESC)
    WHERE trip_id IS NOT NULL;
