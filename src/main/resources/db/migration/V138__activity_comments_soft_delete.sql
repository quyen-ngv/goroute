-- ActivityCommentMapper has always read and written updated_at and is_deleted,
-- but the columns were never created, so every activity-comment query failed with
-- "column c.is_deleted does not exist". Backfill the two columns the mapper expects.
ALTER TABLE activity_comments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Comment listing and the per-trip count both filter on is_deleted.
CREATE INDEX IF NOT EXISTS idx_activity_comments_activity_active
    ON activity_comments(activity_id, created_at)
    WHERE is_deleted = FALSE;
