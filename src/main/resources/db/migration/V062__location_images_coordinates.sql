ALTER TABLE location_images
    ADD COLUMN IF NOT EXISTS latitude DECIMAL(10, 8),
    ADD COLUMN IF NOT EXISTS longitude DECIMAL(11, 8);

UPDATE location_images SET latitude = 21.02850000, longitude = 105.85420000
WHERE latitude IS NULL AND longitude IS NULL AND city_slug = 'hanoi';

UPDATE location_images SET latitude = 10.82310000, longitude = 106.62970000
WHERE latitude IS NULL AND longitude IS NULL AND city_slug = 'hcmc';

UPDATE location_images SET latitude = 16.05440000, longitude = 108.20220000
WHERE latitude IS NULL AND longitude IS NULL AND city_slug = 'danang';

UPDATE location_images SET latitude = 15.88010000, longitude = 108.33800000
WHERE latitude IS NULL AND longitude IS NULL AND city_slug = 'hoian';

UPDATE location_images SET latitude = 16.46370000, longitude = 107.59090000
WHERE latitude IS NULL AND longitude IS NULL AND city_slug = 'hue';

UPDATE location_images SET latitude = 12.23880000, longitude = 109.19670000
WHERE latitude IS NULL AND longitude IS NULL AND city_slug = 'nhatrang';

UPDATE location_images SET latitude = 10.28990000, longitude = 103.98400000
WHERE latitude IS NULL AND longitude IS NULL AND city_slug = 'phuquoc';

CREATE INDEX IF NOT EXISTS idx_location_images_coordinates
    ON location_images(latitude, longitude);
