-- Map search showed place names in another reader's language: map_place_cache held one row
-- per Mapbox place, so a place last described in English (or Korean) was answered in that
-- language to everyone through "picked places" autocomplete and place detail.
-- One row per place and language from now on.
--
-- Rows written before this migration have no known language and get '' (empty): app
-- requests always ask for a language and so no longer see them; server-side lookups, which
-- ask for none, still do. The app's cache refills on the next searches.

ALTER TABLE map_place_cache
    ADD COLUMN IF NOT EXISTS language VARCHAR(16) NOT NULL DEFAULT '';

ALTER TABLE map_place_cache DROP CONSTRAINT IF EXISTS map_place_cache_pkey;
ALTER TABLE map_place_cache ADD PRIMARY KEY (provider, provider_place_id, language);
