-- Flexible schema v1 attributes for place suitability and experience metadata.
-- Attribute values remain JSON strings/booleans/arrays; no PostgreSQL native ENUMs.
ALTER TABLE places ADD COLUMN IF NOT EXISTS attributes JSONB;

COMMENT ON COLUMN places.attributes IS
    'Schema v1 flexible place attributes. Each attribute uses {"value": ..., "description": ...}.';
