-- Food-level narrative sections for detail screen

ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS history_origin TEXT,
    ADD COLUMN IF NOT EXISTS ingredients_preparation TEXT;
