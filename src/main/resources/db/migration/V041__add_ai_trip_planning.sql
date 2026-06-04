CREATE TABLE IF NOT EXISTS user_subscriptions (
    user_id UUID PRIMARY KEY,
    tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ai_trips_used INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_subscriptions_tier CHECK (tier IN ('FREE', 'PRO')),
    CONSTRAINT chk_user_subscriptions_ai_trips_used CHECK (ai_trips_used >= 0)
);

CREATE TABLE IF NOT EXISTS ai_trip_drafts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    trip_name VARCHAR(255),
    city_id VARCHAR(255),
    city_name VARCHAR(255) NOT NULL,
    city_lat DECIMAL(10, 7),
    city_lng DECIMAL(10, 7),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    day_count INTEGER NOT NULL,
    place_groups JSONB NOT NULL DEFAULT '[]'::jsonb,
    pace VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
    preference_text TEXT,
    candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(120),
    created_trip_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_trip_drafts_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_ai_trip_drafts_pace CHECK (pace IN ('RELAXED', 'BALANCED', 'EAGER')),
    CONSTRAINT chk_ai_trip_drafts_day_count CHECK (day_count > 0)
);

CREATE INDEX IF NOT EXISTS idx_ai_trip_drafts_user_status
    ON ai_trip_drafts(user_id, status, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_trip_drafts_user_idempotency
    ON ai_trip_drafts(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE places
    ADD COLUMN IF NOT EXISTS visit_duration_minutes INTEGER;

ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS visit_duration_minutes INTEGER;

COMMENT ON COLUMN places.visit_duration_minutes IS 'Estimated visit duration used by AI trip planning.';
COMMENT ON COLUMN activity_bookings.visit_duration_minutes IS 'Estimated activity duration used by AI trip planning.';
