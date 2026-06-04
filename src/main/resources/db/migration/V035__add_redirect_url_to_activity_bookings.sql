-- Add redirect_url column for affiliate links
ALTER TABLE activity_bookings ADD COLUMN redirect_url TEXT;

-- Set default value = url for existing records
UPDATE activity_bookings SET redirect_url = url WHERE redirect_url IS NULL;
