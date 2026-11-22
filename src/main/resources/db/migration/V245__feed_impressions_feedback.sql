-- Feed ranking, phase P0: collect what the viewer saw and what they asked not to see
-- (.docs/FEED_RANKING_PLAN_2026-10-22.md, .docs/FEED_RANKING_DESIGN_2026-10-22.md §2.2-2.3).
--
-- Additive only: two new tables, their indexes and config rows. The five existing feed
-- endpoints start filtering on feed_feedback and the two Friends endpoints read
-- feed_impressions for the "seen" mark; nothing else changes.
--
-- Plain CREATE INDEX on purpose, as in V214/V232/V242: CONCURRENTLY would wait on the
-- transaction in which Flyway holds its lock.

-- One row per (viewer, post): how often and how long the viewer had the post on screen. Not one
-- row per sighting, so the table grows with "people x posts they reached", not with scrolling.
-- A sighting is the post at least half visible for at least a second (decided in the app);
-- opening the post's detail screen sets opened.
--
-- item_id is user_checkins.id, user_reviews.id or trips.id by item_type. No foreign key, as in
-- saved_posts: a review is hard-deleted, and a missing row only means "not seen".
-- No location column on purpose: the viewer's position is never stored here.
CREATE TABLE IF NOT EXISTS feed_impressions (
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_type     VARCHAR(16) NOT NULL CHECK (item_type IN ('CHECKIN', 'REVIEW', 'TRIP')),
    item_id       UUID        NOT NULL,
    first_seen_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    seen_count    SMALLINT    NOT NULL DEFAULT 1,
    max_dwell_ms  INT         NOT NULL DEFAULT 0,
    opened        BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, item_type, item_id)
);

-- "What did this viewer see lately", for the seen filter of the ranked feed (P3).
CREATE INDEX IF NOT EXISTS idx_feed_impressions_user_last
    ON feed_impressions (user_id, last_seen_at DESC);

-- The retention job deletes by age.
CREATE INDEX IF NOT EXISTS idx_feed_impressions_last
    ON feed_impressions (last_seen_at);

-- "Hide this post" and "Hide posts from this person for 30 days" (D10). Undo sets revoked_at
-- rather than deleting, so the ranking can later count hides as a negative signal.
CREATE TABLE IF NOT EXISTS feed_feedback (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind           VARCHAR(20) NOT NULL CHECK (kind IN ('HIDE_POST', 'HIDE_AUTHOR')),
    item_type      VARCHAR(16) CHECK (item_type IN ('CHECKIN', 'REVIEW', 'TRIP')),
    item_id        UUID,
    target_user_id UUID,
    expires_at     TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    revoked_at     TIMESTAMP,
    CONSTRAINT chk_feed_feedback_target CHECK (
        (kind = 'HIDE_POST' AND item_type IS NOT NULL AND item_id IS NOT NULL)
        OR (kind = 'HIDE_AUTHOR' AND target_user_id IS NOT NULL AND expires_at IS NOT NULL))
);

-- Every feed query asks "what has this viewer hidden that is still in force".
CREATE INDEX IF NOT EXISTS idx_feed_feedback_user_active
    ON feed_feedback (user_id, kind)
    WHERE revoked_at IS NULL;

-- Counting hides per post, for the ranking's negative factor.
CREATE INDEX IF NOT EXISTS idx_feed_feedback_item
    ON feed_feedback (item_type, item_id)
    WHERE kind = 'HIDE_POST' AND revoked_at IS NULL;

INSERT INTO config (label, key, value, description, is_active)
VALUES ('FEED', 'IMPRESSION_RETENTION_DAYS', '90',
        'Giu luot hien cua Feed bao nhieu ngay roi xoa (7-365)', TRUE),
       ('FEED', 'HIDE_AUTHOR_DAYS', '30',
        'An bai tu mot nguoi trong bao nhieu ngay (1-365)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
