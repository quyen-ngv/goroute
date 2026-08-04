ALTER TABLE places
    ADD COLUMN IF NOT EXISTS ai_references JSONB;

COMMENT ON COLUMN places.ai_references IS
    'AI-curated metadata for article and review sources used to enrich the place';
