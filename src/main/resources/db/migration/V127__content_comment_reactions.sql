CREATE TABLE content_comment_likes (
    comment_id UUID NOT NULL REFERENCES content_comments(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX idx_content_comment_likes_comment ON content_comment_likes(comment_id);

CREATE INDEX idx_content_comments_parent_page
    ON content_comments(content_type, content_id, parent_id, created_at, id);
