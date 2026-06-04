-- Update place table
ALTER TABLE places ADD COLUMN IF NOT EXISTS place_group VARCHAR(30);
ALTER TABLE places ADD COLUMN IF NOT EXISTS avg_authenticity_score DECIMAL(4,3);
ALTER TABLE places ADD COLUMN IF NOT EXISTS place_overall_score DECIMAL(4,3);
ALTER TABLE places ADD COLUMN IF NOT EXISTS adjusted_rating DECIMAL(3,2);
ALTER TABLE places ADD COLUMN IF NOT EXISTS trust_level VARCHAR(20);
ALTER TABLE places ADD COLUMN IF NOT EXISTS is_jcurve_detected BOOLEAN DEFAULT FALSE;
ALTER TABLE places ADD COLUMN IF NOT EXISTS is_spike_detected BOOLEAN DEFAULT FALSE;
ALTER TABLE places ADD COLUMN IF NOT EXISTS authentic_low_star_count INTEGER DEFAULT 0;
ALTER TABLE places ADD COLUMN IF NOT EXISTS score_calculated_at TIMESTAMP;

-- Update place_reviews table
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS review_id VARCHAR(255) UNIQUE;
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS google_place_id VARCHAR(255);
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS profile_url VARCHAR(500);
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS is_local_guide BOOLEAN DEFAULT FALSE;
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS total_reviews INTEGER DEFAULT 0;
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS total_photos INTEGER DEFAULT 0;
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS language VARCHAR(10);
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS likes INTEGER DEFAULT 0;
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS content_hash VARCHAR(255);
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS authenticity_score DECIMAL(4,3);
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS authenticity_level VARCHAR(10);
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS score_calculated_at TIMESTAMP;
ALTER TABLE place_reviews ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_place_reviews_review_id ON place_reviews(review_id);
CREATE INDEX IF NOT EXISTS idx_place_reviews_google_place_id ON place_reviews(google_place_id);
CREATE INDEX IF NOT EXISTS idx_place_reviews_authenticity_score ON place_reviews(authenticity_score);
CREATE INDEX IF NOT EXISTS idx_place_reviews_score_calculated_at ON place_reviews(score_calculated_at);
CREATE INDEX IF NOT EXISTS idx_place_trust_level ON places(trust_level);
CREATE INDEX IF NOT EXISTS idx_place_overall_score ON places(place_overall_score);
CREATE INDEX IF NOT EXISTS idx_place_score_calculated_at ON places(score_calculated_at);

-- Add comments
COMMENT ON COLUMN places.place_group IS 'Category group: FOOD_AND_DRINK, CULTURE_AND_HERITAGE, NATURE_AND_OUTDOORS, SHOPPING_AND_MARKET, ATTRACTIONS, ACCOMMODATION, OTHER';
COMMENT ON COLUMN places.avg_authenticity_score IS 'Average authenticity score of all reviews';
COMMENT ON COLUMN places.place_overall_score IS 'Overall trust score calculated from reviews';
COMMENT ON COLUMN places.adjusted_rating IS 'Rating adjusted by place_overall_score';
COMMENT ON COLUMN places.trust_level IS 'Trust level: TRUSTED, MODERATE, CAUTION, SUSPICIOUS';
COMMENT ON COLUMN places.is_jcurve_detected IS 'J-curve pattern detected in rating distribution';
COMMENT ON COLUMN places.is_spike_detected IS 'Review flooding detected';
COMMENT ON COLUMN places.authentic_low_star_count IS 'Count of authentic low-star reviews';
COMMENT ON COLUMN places.score_calculated_at IS 'Last time scores were calculated';

COMMENT ON COLUMN place_reviews.review_id IS 'Google Review ID for deduplication';
COMMENT ON COLUMN place_reviews.google_place_id IS 'Google Place ID reference';
COMMENT ON COLUMN place_reviews.profile_url IS 'Reviewer profile URL';
COMMENT ON COLUMN place_reviews.is_local_guide IS 'Is reviewer a Google Local Guide';
COMMENT ON COLUMN place_reviews.total_reviews IS 'Total reviews by this reviewer';
COMMENT ON COLUMN place_reviews.total_photos IS 'Total photos uploaded by this reviewer';
COMMENT ON COLUMN place_reviews.language IS 'Language: VI, EN, JA, KO, ZH, OTHER';
COMMENT ON COLUMN place_reviews.likes IS 'Number of likes on this review';
COMMENT ON COLUMN place_reviews.content_hash IS 'Hash of review content for change detection';
COMMENT ON COLUMN place_reviews.is_deleted IS 'Is review deleted on Google';
COMMENT ON COLUMN place_reviews.authenticity_score IS 'Calculated authenticity score (0-1)';
COMMENT ON COLUMN place_reviews.authenticity_level IS 'Authenticity level: HIGH, MEDIUM, LOW';
COMMENT ON COLUMN place_reviews.score_calculated_at IS 'Last time authenticity score was calculated';
