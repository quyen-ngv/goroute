-- Food discovery tables + location_images.city_slug

ALTER TABLE location_images
    ADD COLUMN IF NOT EXISTS city_slug VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS idx_location_images_city_slug
    ON location_images(city_slug)
    WHERE city_slug IS NOT NULL AND city_slug <> '';

UPDATE location_images SET city_slug = 'hanoi' WHERE city_slug IS NULL AND full_address ILIKE '%hà nội%';
UPDATE location_images SET city_slug = 'hcmc' WHERE city_slug IS NULL AND (full_address ILIKE '%hồ chí minh%' OR full_address ILIKE '%ho chi minh%' OR full_address ILIKE '%sài gòn%' OR full_address ILIKE '%saigon%');
UPDATE location_images SET city_slug = 'danang' WHERE city_slug IS NULL AND full_address ILIKE '%đà nẵng%';
UPDATE location_images SET city_slug = 'hoian' WHERE city_slug IS NULL AND full_address ILIKE '%hội an%';
UPDATE location_images SET city_slug = 'hue' WHERE city_slug IS NULL AND full_address ILIKE '%huế%';
UPDATE location_images SET city_slug = 'nhatrang' WHERE city_slug IS NULL AND full_address ILIKE '%nha trang%';
UPDATE location_images SET city_slug = 'phuquoc' WHERE city_slug IS NULL AND full_address ILIKE '%phú quốc%';

CREATE TABLE IF NOT EXISTS foods (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_vi          VARCHAR(100) NOT NULL,
    name_en          VARCHAR(100) NOT NULL,
    name_ja          VARCHAR(100),
    name_ko          VARCHAR(100),
    description      TEXT NOT NULL,
    category         VARCHAR(50) NOT NULL,
    image_url        TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_foods_category ON foods(category);

CREATE TABLE IF NOT EXISTS food_city_scores (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    food_id           UUID NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    city_slug         VARCHAR(100) NOT NULL,
    score             INT NOT NULL CHECK (score BETWEEN 0 AND 100),
    local_description TEXT,
    flavor_profile    JSONB,
    fun_fact          TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (food_id, city_slug)
);

CREATE INDEX IF NOT EXISTS idx_food_city_scores_city_score
    ON food_city_scores(city_slug, score DESC);

CREATE TABLE IF NOT EXISTS place_foods (
    place_id  UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    food_id   UUID NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    PRIMARY KEY (place_id, food_id)
);

CREATE INDEX IF NOT EXISTS idx_place_foods_food_id ON place_foods(food_id);
CREATE INDEX IF NOT EXISTS idx_place_foods_place_id ON place_foods(place_id);
