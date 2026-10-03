-- Who looks social-video places up on Google Maps. SCRAPE keeps the worker's browser
-- search, which is what every job did before this row existed. GOOGLE sends the worker to
-- the backend's Places API search, and only takes effect once the backend has a Google
-- Places key; without one the backend keeps telling the worker to scrape.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('SOCIAL_LOCATION', 'MAP_SEARCH_PROVIDER', 'SCRAPE',
     'Google Maps lookup for social-video places: SCRAPE (worker browser) or GOOGLE (backend Places API, needs GOOGLE_PLACES_API_KEY)',
     TRUE)
ON CONFLICT (label, key) DO NOTHING;
