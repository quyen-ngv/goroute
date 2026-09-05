ALTER TABLE social_location_jobs
    ADD COLUMN IF NOT EXISTS operation VARCHAR(32) NOT NULL DEFAULT 'GEN_ITINERARY',
    ADD COLUMN IF NOT EXISTS ai_trip_job_id UUID,
    ADD COLUMN IF NOT EXISTS saved_spot_count INTEGER;

ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS option_group_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS option_index INTEGER,
    ADD COLUMN IF NOT EXISTS relation VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_social_job_id UUID,
    ADD COLUMN IF NOT EXISTS source_social_candidate_ref VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_activities_social_source
    ON activities(source_social_job_id, source_social_candidate_ref)
    WHERE source_social_job_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_activities_trip_option_group
    ON activities(trip_id, day_number, option_group_id)
    WHERE option_group_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_social_location_jobs_ai_trip_job_id
    ON social_location_jobs(ai_trip_job_id)
    WHERE ai_trip_job_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS social_saved_spots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    social_job_id UUID NOT NULL REFERENCES social_location_jobs(id) ON DELETE CASCADE,
    candidate_ref VARCHAR(128) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    query VARCHAR(500),
    description TEXT,
    useful_info JSONB,
    visit_guidance JSONB,
    address_hint VARCHAR(500),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    google_place_id VARCHAR(255),
    place_id UUID,
    day_hint INTEGER,
    sequence INTEGER NOT NULL,
    time_hint VARCHAR(64),
    option_group_id VARCHAR(128),
    option_index INTEGER,
    relation VARCHAR(32),
    identity_status VARCHAR(32),
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED',
    evidence JSONB,
    image_url VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_saved_spots_job_candidate UNIQUE (user_id, social_job_id, candidate_ref)
);

CREATE INDEX IF NOT EXISTS idx_social_saved_spots_user_created
    ON social_saved_spots(user_id, created_at DESC, sequence);
CREATE INDEX IF NOT EXISTS idx_social_saved_spots_job_sequence
    ON social_saved_spots(social_job_id, sequence);

INSERT INTO config (label, key, value, description, is_active) VALUES
    ('AI_TRIP', 'SOCIAL_START_OFFSET_DAYS', '5',
     'So ngay tu hien tai den ngay bat dau itinerary tao tu video social (0..30)', TRUE),
    ('LOCATION_IMAGE', 'DEFAULT_IMAGE_URL', 'https://images.unsplash.com/photo-1488646953014-85cb44e25828',
     'Anh cover dung chung khi khong tim thay location image cua destination', TRUE)
ON CONFLICT (label, key) DO NOTHING;
