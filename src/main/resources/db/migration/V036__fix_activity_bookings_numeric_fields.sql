-- Fix numeric field overflow in activity_bookings table
-- Update duration_hours, rating, review_count, booked_count to support larger values

ALTER TABLE activity_bookings
    ALTER COLUMN duration_hours TYPE DECIMAL(10,2),
    ALTER COLUMN rating TYPE DECIMAL(5,2),
    ALTER COLUMN review_count TYPE BIGINT,
    ALTER COLUMN booked_count TYPE BIGINT;
