-- Change food price fields from BIGINT to NUMERIC to support decimal values

ALTER TABLE foods
    ALTER COLUMN price_min TYPE NUMERIC(12, 2),
    ALTER COLUMN price_max TYPE NUMERIC(12, 2);

COMMENT ON COLUMN foods.price_min IS 'Minimum price with up to 2 decimal places';
COMMENT ON COLUMN foods.price_max IS 'Maximum price with up to 2 decimal places';
