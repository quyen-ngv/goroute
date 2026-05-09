-- Add is_helpful column to track helpful vs unhelpful votes
ALTER TABLE review_helpful_votes
ADD COLUMN is_helpful BOOLEAN DEFAULT true NOT NULL;

-- Add unhelpful_votes column to user_reviews
ALTER TABLE user_reviews
ADD COLUMN unhelpful_votes INTEGER DEFAULT 0 NOT NULL;

-- Create index for efficient counting
CREATE INDEX idx_review_helpful_votes_is_helpful ON review_helpful_votes(review_id, is_helpful);
