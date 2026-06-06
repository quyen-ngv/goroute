ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS helpful_votes INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS unhelpful_votes INTEGER DEFAULT 0 NOT NULL;

CREATE TABLE IF NOT EXISTS trip_helpful_votes (
    trip_id UUID NOT NULL,
    user_id UUID NOT NULL,
    is_helpful BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (trip_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_helpful_votes_trip ON trip_helpful_votes(trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_helpful_votes_user ON trip_helpful_votes(user_id);
