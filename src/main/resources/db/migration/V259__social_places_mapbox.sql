-- Social-video places are no longer scraped from Google Maps (28/10/2026). The worker asks the
-- backend, which answers from our catalogue first and from Mapbox otherwise; a Mapbox place is
-- not written to `places`, it stays on the activity as name, address, coordinates and this id.
ALTER TABLE activities ADD COLUMN IF NOT EXISTS mapbox_id VARCHAR(255);

-- The SCRAPE / GOOGLE switch (V205) is gone with the scraper.
DELETE FROM config WHERE label = 'SOCIAL_LOCATION' AND key = 'MAP_SEARCH_PROVIDER';
