-- Other people's posts a user saved to read again later (epic 06, §2.6.2): check-ins and
-- reviews. A save is private to the saver and the author is never told.
--
-- post_id is user_checkins.id when post_type = 'CHECKIN' and user_reviews.id when 'REVIEW'.
-- No foreign key on purpose: a review is hard-deleted and a check-in soft-deleted, and in
-- both cases the save stays so the list can say "no longer available" until the user
-- removes it, instead of the post silently vanishing from under them.
CREATE TABLE IF NOT EXISTS saved_posts (
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_type  VARCHAR(20) NOT NULL CHECK (post_type IN ('CHECKIN', 'REVIEW')),
    post_id    UUID        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, post_type, post_id)
);

-- The saved list pages by (created_at, post_type, post_id), all three in the same direction,
-- so one index serves newest-first (scanned backwards) and oldest-first.
CREATE INDEX IF NOT EXISTS idx_saved_posts_user_created
    ON saved_posts (user_id, created_at, post_type, post_id);
