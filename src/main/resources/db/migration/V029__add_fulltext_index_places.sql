-- Add GIN index for PostgreSQL full-text search (simple config for multi-language support)
CREATE INDEX idx_places_fulltext ON places USING GIN (
    to_tsvector('simple', COALESCE(title, '') || ' ' || COALESCE(address, '') || ' ' || COALESCE(category, '') || ' ' || COALESCE(descriptions, ''))
);

-- Add trigram index for better LIKE performance (supports Vietnamese)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_places_title_trgm ON places USING GIN (title gin_trgm_ops);
CREATE INDEX idx_places_address_trgm ON places USING GIN (address gin_trgm_ops);
