-- Per-user limits on the app's Goong search proxy (/v1/api/goong/autocomplete and
-- /v1/api/goong/place-detail). Every forwarded call is a paid Goong request.
--
-- One row per user, day (Asia/Ho_Chi_Minh) and operation. request_count is the number of
-- calls forwarded to Goong and is what the daily limit is checked against; rejected_count
-- is the number refused by either limit, kept so operators can see who hits the ceiling.
-- The per-minute window lives in memory only. Rows older than
-- MAP_SEARCH/USAGE_RETENTION_DAYS are purged nightly.
CREATE TABLE IF NOT EXISTS map_search_usage_daily (
    user_id        UUID        NOT NULL,
    usage_date     DATE        NOT NULL,
    operation      VARCHAR(32) NOT NULL,
    request_count  INTEGER     NOT NULL DEFAULT 0,
    rejected_count INTEGER     NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, usage_date, operation),
    CONSTRAINT chk_map_search_usage_operation CHECK (operation IN ('AUTOCOMPLETE', 'PLACE_DETAIL')),
    CONSTRAINT chk_map_search_usage_counts CHECK (request_count >= 0 AND rejected_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_map_search_usage_date ON map_search_usage_daily(usage_date);

INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('MAP_SEARCH', 'RATE_LIMIT_ENABLED', 'true', 'Bat gioi han so lan tim kiem Goong (autocomplete, place detail) moi user', TRUE),
    ('MAP_SEARCH', 'EXEMPT_ADMINS', 'true', 'Tai khoan admin console khong bi gioi han', TRUE),
    ('MAP_SEARCH', 'AUTOCOMPLETE_PER_MINUTE', '30', 'So lan goi autocomplete toi da moi user trong 1 phut (1-600)', TRUE),
    ('MAP_SEARCH', 'AUTOCOMPLETE_PER_DAY', '300', 'So lan goi autocomplete toi da moi user trong 1 ngay (1-100000)', TRUE),
    ('MAP_SEARCH', 'PLACE_DETAIL_PER_MINUTE', '30', 'So lan goi place detail toi da moi user trong 1 phut (1-600)', TRUE),
    ('MAP_SEARCH', 'PLACE_DETAIL_PER_DAY', '200', 'So lan goi place detail toi da moi user trong 1 ngay (1-100000)', TRUE),
    ('MAP_SEARCH', 'USAGE_RETENTION_DAYS', '90', 'So ngay giu bang dem map_search_usage_daily (7-730)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
