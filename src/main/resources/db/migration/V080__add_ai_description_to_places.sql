-- Add ai_description column for AI trip planning
-- Initially copy from descriptions, then can be manually curated for better AI context

ALTER TABLE places ADD COLUMN IF NOT EXISTS ai_description TEXT;

-- Backfill from descriptions
UPDATE places SET ai_description = descriptions WHERE ai_description IS NULL AND descriptions IS NOT NULL;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_places_ai_description ON places USING gin(to_tsvector('english', COALESCE(ai_description, '')));

COMMENT ON COLUMN places.ai_description IS 'Curated description optimized for AI trip planning context. Can be manually edited in admin panel.';
