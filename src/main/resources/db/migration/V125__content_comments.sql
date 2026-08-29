-- One reply tree for the social surfaces that people can open publicly.  A polymorphic
-- target avoids four duplicated tables while parent_id gives us replies of any depth.
CREATE TABLE content_comments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(50) NOT NULL CHECK (content_type IN ('TRIP', 'CHECKIN', 'REVIEW', 'PLACE_COLLECTION')),
    content_id   UUID NOT NULL,
    parent_id    UUID REFERENCES content_comments(id) ON DELETE SET NULL,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content      VARCHAR(1000),
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_content_comments_target ON content_comments(content_type, content_id, created_at);
CREATE INDEX idx_content_comments_parent ON content_comments(parent_id) WHERE parent_id IS NOT NULL;
