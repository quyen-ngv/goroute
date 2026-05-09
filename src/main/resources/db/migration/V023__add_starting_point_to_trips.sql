-- Add starting point fields to trips table
ALTER TABLE trips ADD COLUMN IF NOT EXISTS starting_point_name VARCHAR(255);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS starting_point_address VARCHAR(500);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS starting_point_lat DECIMAL(10,7);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS starting_point_lng DECIMAL(10,7);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS starting_point_time TIMESTAMP;
