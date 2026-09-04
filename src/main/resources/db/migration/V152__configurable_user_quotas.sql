-- Runtime defaults for the three user-facing quota families.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('TRIP', 'FREE_TRIP_QUOTA', '3', 'So trip tao thu cong mien phi cho moi user (0..1000)', TRUE),
    ('AI_TRIP_QUOTA', 'FREE_TRIP_QUOTA', '3', 'So luot tao trip bang AI cua user FREE (0..1000)', TRUE),
    ('AI_TRIP_QUOTA', 'PRO_TRIP_QUOTA', '10', 'So luot tao trip bang AI cua user PRO (0..1000)', TRUE),
    ('SOCIAL_LOCATION', 'DAILY_JOB_LIMIT_DEFAULT', '5', 'So luot extract spots tu video social moi user moi ngay (0..1000)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- A nullable field inherits the global config. Keeping overrides in their own row means adding a
-- user exception never changes the meaning of the shared config table.
CREATE TABLE IF NOT EXISTS user_quota_overrides (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    free_trip_quota INTEGER,
    ai_trip_free_quota INTEGER,
    ai_trip_pro_quota INTEGER,
    social_location_daily_limit INTEGER,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_quota_override_free_trip
        CHECK (free_trip_quota IS NULL OR free_trip_quota BETWEEN 0 AND 1000),
    CONSTRAINT chk_user_quota_override_ai_free
        CHECK (ai_trip_free_quota IS NULL OR ai_trip_free_quota BETWEEN 0 AND 1000),
    CONSTRAINT chk_user_quota_override_ai_pro
        CHECK (ai_trip_pro_quota IS NULL OR ai_trip_pro_quota BETWEEN 0 AND 1000),
    CONSTRAINT chk_user_quota_override_social
        CHECK (social_location_daily_limit IS NULL OR social_location_daily_limit BETWEEN 0 AND 1000)
);
