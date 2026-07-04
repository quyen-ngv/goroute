-- Food detail redesign: per-food theme color, category tags, hero tagline (multi-language),
-- and richer region cards (region grouping + short description). All columns nullable => backward-compatible.

ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS theme_color VARCHAR(9),
    ADD COLUMN IF NOT EXISTS tags TEXT,
    ADD COLUMN IF NOT EXISTS hero_tagline_vi VARCHAR(200),
    ADD COLUMN IF NOT EXISTS hero_tagline_en VARCHAR(200),
    ADD COLUMN IF NOT EXISTS hero_tagline_ja VARCHAR(200),
    ADD COLUMN IF NOT EXISTS hero_tagline_ko VARCHAR(200);

-- Region cards: which region a city belongs to (north/central/south) and a short teaser.
ALTER TABLE food_city_scores
    ADD COLUMN IF NOT EXISTS region_key VARCHAR(20),
    ADD COLUMN IF NOT EXISTS short_description VARCHAR(300);
