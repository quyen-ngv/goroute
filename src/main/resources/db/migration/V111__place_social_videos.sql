CREATE TABLE IF NOT EXISTS place_social_videos (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    social_job_id UUID NOT NULL REFERENCES social_location_jobs(id) ON DELETE CASCADE,
    source_url TEXT NOT NULL,
    canonical_url TEXT,
    platform VARCHAR(32) NOT NULL,
    title TEXT,
    thumbnail_url TEXT,
    creator_name TEXT,
    video_summary TEXT,
    video_useful_summary TEXT,
    general_guidance JSONB NOT NULL DEFAULT '[]'::jsonb,
    place_recap TEXT,
    useful_info JSONB NOT NULL DEFAULT '[]'::jsonb,
    visit_guidance JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_sources JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_text JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence NUMERIC(5,4),
    language VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_place_social_videos_place_job UNIQUE (place_id, social_job_id)
);

CREATE INDEX IF NOT EXISTS idx_place_social_videos_place_updated
    ON place_social_videos(place_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_place_social_videos_social_job
    ON place_social_videos(social_job_id);
