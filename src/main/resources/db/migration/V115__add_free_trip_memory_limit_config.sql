INSERT INTO config (label, key, value, description, is_active)
VALUES (
    'TRIP_MEMORY',
    'FREE_TRIP_MEMORY_LIMIT',
    '50',
    'Maximum memory photos for trips owned by free-tier users',
    TRUE
)
ON CONFLICT (label, key) DO NOTHING;
