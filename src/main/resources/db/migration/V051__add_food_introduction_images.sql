ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS introduction_images TEXT;

ALTER TABLE food_city_scores
    ADD COLUMN IF NOT EXISTS introduction_images TEXT;
