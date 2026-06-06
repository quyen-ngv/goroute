ALTER TABLE user_reviews
    ADD COLUMN activity_booking_id UUID REFERENCES activity_bookings(id) ON DELETE CASCADE;

ALTER TABLE user_reviews
    ALTER COLUMN place_id DROP NOT NULL;

ALTER TABLE user_reviews
    ADD CONSTRAINT chk_user_reviews_one_target
    CHECK (
        (place_id IS NOT NULL AND activity_booking_id IS NULL)
        OR (place_id IS NULL AND activity_booking_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_user_reviews_user_activity_booking
    ON user_reviews(user_id, activity_booking_id)
    WHERE activity_booking_id IS NOT NULL;

CREATE INDEX idx_user_reviews_activity_booking
    ON user_reviews(activity_booking_id);
