INSERT INTO config (label, key, value, description, is_active)
VALUES (
    'PLACE_REVIEW',
    'DEFAULT_REFRESH_MAX_REVIEWS',
    '200',
    'Default maximum review count for an administrator-triggered place review refresh',
    TRUE
)
ON CONFLICT (label, key) DO NOTHING;
