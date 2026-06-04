-- Support transport activities that span from point A to point B
-- (e.g. driving Hanoi -> Halong). For non-transport activities these fields stay NULL.
ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS end_lat DECIMAL(10,7),
    ADD COLUMN IF NOT EXISTS end_lng DECIMAL(10,7),
    ADD COLUMN IF NOT EXISTS end_address VARCHAR(500);
