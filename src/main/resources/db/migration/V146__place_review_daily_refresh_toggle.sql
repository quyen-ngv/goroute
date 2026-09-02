INSERT INTO config (label, key, value, description, is_active)
VALUES (
    'PLACE_REVIEW',
    'DAILY_REFRESH_ENABLED',
    'true',
    'Allow the scraper worker to run its daily place review refresh. Set to false to skip the scheduled run; administrator-triggered refreshes still work',
    TRUE
)
ON CONFLICT (label, key) DO NOTHING;
