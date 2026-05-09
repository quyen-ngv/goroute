-- Create comprehensive user review system for TripMind
-- This is separate from place_reviews (Google Maps data)

-- User reviews table (reviews from TripMind users)
CREATE TABLE user_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    trip_id UUID REFERENCES trips(id) ON DELETE SET NULL,
    
    -- Overall rating (required)
    overall_rating SMALLINT NOT NULL CHECK (overall_rating BETWEEN 1 AND 5),
    
    -- Aspect ratings (optional)
    food_rating SMALLINT CHECK (food_rating IS NULL OR (food_rating BETWEEN 1 AND 5)),
    price_rating SMALLINT CHECK (price_rating IS NULL OR (price_rating BETWEEN 1 AND 5)),
    ambiance_rating SMALLINT CHECK (ambiance_rating IS NULL OR (ambiance_rating BETWEEN 1 AND 5)),
    service_rating SMALLINT CHECK (service_rating IS NULL OR (service_rating BETWEEN 1 AND 5)),
    
    -- Review content (optional)
    text TEXT,
    photos TEXT, -- JSON array of photo URLs
    
    -- Scoring metadata
    weight DECIMAL(3,2) DEFAULT 1.0,
    helpful_votes INT DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- One review per user per place
    UNIQUE(user_id, place_id)
);

-- User review profiles (tier & trust score)
CREATE TABLE user_review_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    
    -- Tier system
    tier VARCHAR(20) DEFAULT 'NEWCOMER' CHECK (tier IN ('NEWCOMER', 'TRAVELER', 'EXPLORER', 'EXPERT')),
    
    -- Trust score (0-100)
    trust_score DECIMAL(5,2) DEFAULT 0 CHECK (trust_score >= 0 AND trust_score <= 100),
    
    -- Stats for trust calculation
    review_count INT DEFAULT 0,
    avg_review_length INT DEFAULT 0,
    helpful_votes_received INT DEFAULT 0,
    verified_trips_count INT DEFAULT 0,
    
    -- Timestamps
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Place scores (aggregated scores from user reviews)
CREATE TABLE place_scores (
    place_id UUID PRIMARY KEY REFERENCES places(id) ON DELETE CASCADE,
    
    -- Scores
    tripmind_score DECIMAL(2,1) CHECK (tripmind_score IS NULL OR (tripmind_score >= 1.0 AND tripmind_score <= 5.0)),
    google_score DECIMAL(2,1) CHECK (google_score IS NULL OR (google_score >= 1.0 AND google_score <= 5.0)),
    
    -- Review count
    review_count INT DEFAULT 0,
    
    -- Aspect scores
    food_score DECIMAL(2,1) CHECK (food_score IS NULL OR (food_score >= 1.0 AND food_score <= 5.0)),
    price_score DECIMAL(2,1) CHECK (price_score IS NULL OR (price_score >= 1.0 AND price_score <= 5.0)),
    ambiance_score DECIMAL(2,1) CHECK (ambiance_score IS NULL OR (ambiance_score >= 1.0 AND ambiance_score <= 5.0)),
    service_score DECIMAL(2,1) CHECK (service_score IS NULL OR (service_score >= 1.0 AND service_score <= 5.0)),
    
    -- Nationality breakdown (JSON: {"KR": 4.5, "JP": 4.3, "VN": 4.7})
    nationality_breakdown TEXT,
    
    -- Timestamps
    last_calculated_at TIMESTAMP DEFAULT NOW()
);

-- Review flags (fraud detection)
CREATE TABLE review_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES user_reviews(id) ON DELETE CASCADE,
    
    -- Flag type
    flag_type VARCHAR(50) NOT NULL CHECK (flag_type IN (
        'HIGH_VELOCITY',
        'SUSPICIOUS_NEW_ACCOUNT', 
        'IP_CLUSTER',
        'NO_TRIP_CONTEXT',
        'DUPLICATE_TEXT'
    )),
    
    -- Severity
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    
    -- Details
    reason TEXT,
    metadata TEXT, -- JSON for additional context
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT NOW()
);

-- Review helpful votes
CREATE TABLE review_helpful_votes (
    review_id UUID NOT NULL REFERENCES user_reviews(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    
    PRIMARY KEY (review_id, user_id)
);

-- Indexes for performance
CREATE INDEX idx_user_reviews_place ON user_reviews(place_id);
CREATE INDEX idx_user_reviews_user ON user_reviews(user_id);
CREATE INDEX idx_user_reviews_trip ON user_reviews(trip_id);
CREATE INDEX idx_user_reviews_created ON user_reviews(created_at DESC);
CREATE INDEX idx_user_reviews_rating ON user_reviews(overall_rating);

CREATE INDEX idx_review_flags_review ON review_flags(review_id);
CREATE INDEX idx_review_flags_type ON review_flags(flag_type);
CREATE INDEX idx_review_flags_severity ON review_flags(severity);

CREATE INDEX idx_review_helpful_votes_review ON review_helpful_votes(review_id);
CREATE INDEX idx_review_helpful_votes_user ON review_helpful_votes(user_id);

CREATE INDEX idx_place_scores_tripmind ON place_scores(tripmind_score DESC);
CREATE INDEX idx_place_scores_review_count ON place_scores(review_count DESC);

-- Initialize user_review_profiles for existing users
INSERT INTO user_review_profiles (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

-- Initialize place_scores for existing places with Google ratings
INSERT INTO place_scores (place_id, google_score, review_count)
SELECT id, review_rating, review_count 
FROM places 
WHERE review_rating IS NOT NULL
ON CONFLICT (place_id) DO NOTHING;
