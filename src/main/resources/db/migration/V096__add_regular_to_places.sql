ALTER TABLE places
    ADD COLUMN IF NOT EXISTS regular TEXT;

COMMENT ON COLUMN places.regular IS 'Regular opening hours JSON as text.';
