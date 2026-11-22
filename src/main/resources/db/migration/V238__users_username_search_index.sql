-- People search (GET /v1/api/users/search): username prefix match on lower(username).
CREATE INDEX IF NOT EXISTS idx_users_username_lower_prefix
    ON users (lower(username) text_pattern_ops)
    WHERE deleted_at IS NULL;
