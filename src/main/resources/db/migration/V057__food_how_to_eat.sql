-- How to eat guide: general description + step images with per-image captions

ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS how_to_eat_description TEXT,
    ADD COLUMN IF NOT EXISTS how_to_eat_steps TEXT;
