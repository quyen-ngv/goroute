CREATE TABLE user_checkin_likes (
    checkin_id UUID NOT NULL REFERENCES user_checkins(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (checkin_id, user_id)
);

CREATE INDEX idx_user_checkin_likes_user ON user_checkin_likes(user_id, created_at DESC);
