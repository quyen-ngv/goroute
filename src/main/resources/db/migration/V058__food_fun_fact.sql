-- Fun fact is food-level content, not per city.
ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS fun_fact TEXT;

-- Migrate existing city-level fun facts to food (highest score per food).
UPDATE foods f
SET fun_fact = sub.fun_fact
FROM (
    SELECT DISTINCT ON (food_id) food_id, fun_fact
    FROM food_city_scores
    WHERE fun_fact IS NOT NULL AND TRIM(fun_fact) <> ''
    ORDER BY food_id, score DESC
) sub
WHERE f.id = sub.food_id
  AND (f.fun_fact IS NULL OR TRIM(f.fun_fact) = '');
