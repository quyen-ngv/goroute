-- Followers are told when someone they follow checks in or shares a trip publicly (epic 12).
--
-- Additive only: two nullable columns, one new table, two indexes and config rows. Nothing that
-- exists today reads any of it, and the feature stays off (SOCIAL.FOLLOWER_FANOUT_ENABLED = false)
-- for everyone except USER.BETA_USER until an operator switches it on.
--
-- Plain CREATE INDEX on purpose, as in V214/V232: CONCURRENTLY would wait on the transaction in
-- which Flyway holds its lock.

-- One row per recipient per post. The fan-out inserts with ON CONFLICT DO NOTHING on this key, so
-- the same post can never notify the same follower twice, whoever replays it. Every existing row
-- leaves it NULL and is outside the index.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_dedupe
    ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- A trip notifies its owner's followers the first time it becomes PUBLIC, never again:
-- public_shared_at moves on every private -> public toggle, so it cannot be the guard.
ALTER TABLE trips ADD COLUMN IF NOT EXISTS followers_notified_at TIMESTAMP;

-- Trips that are already public were shared before this feature existed; making one private and
-- public again must not announce it as new.
UPDATE trips
SET followers_notified_at = COALESCE(public_shared_at, created_at)
WHERE visibility = 'PUBLIC'
  AND followers_notified_at IS NULL;

-- The fan-out walks an author's followers in follower_id order, a chunk at a time.
CREATE INDEX IF NOT EXISTS idx_user_follows_following_follower
    ON user_follows (following_id, follower_id);

-- Trace of every fan-out. Written by the event listener as it goes; nothing scans it to do work.
-- A row left RUNNING after a restart says exactly where the fan-out stopped (last_follower_id).
CREATE TABLE IF NOT EXISTS follower_fanout_logs (
    id               UUID PRIMARY KEY,
    post_type        VARCHAR(20)  NOT NULL,
    post_id          UUID         NOT NULL,
    author_id        UUID         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    -- Whether this fan-out was allowed to push; false inside the author's cooldown.
    push_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Posts of the author announced by this push (1, or more when earlier ones were held back).
    post_count       INT          NOT NULL DEFAULT 1,
    follower_total   INT          NOT NULL DEFAULT 0,
    notified_count   INT          NOT NULL DEFAULT 0,
    pushed_count     INT          NOT NULL DEFAULT 0,
    last_follower_id UUID,
    error            VARCHAR(1000),
    started_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at      TIMESTAMP,
    CONSTRAINT follower_fanout_logs_post_type_check CHECK (post_type IN ('CHECKIN', 'TRIP')),
    CONSTRAINT follower_fanout_logs_status_check
        CHECK (status IN ('RUNNING', 'DONE', 'FAILED', 'CANCELLED', 'SKIPPED'))
);

-- The cooldown asks "when did this author last push", newest first.
CREATE INDEX IF NOT EXISTS idx_follower_fanout_logs_author
    ON follower_fanout_logs (author_id, started_at DESC);

INSERT INTO config (label, key, value, description, is_active)
VALUES ('SOCIAL', 'FOLLOWER_FANOUT_ENABLED', 'false',
        'Bao cho follower khi nguoi ho theo doi check-in / chia se trip; khi tat chi USER.BETA_USER nhan', TRUE),
       ('SOCIAL', 'FOLLOWER_PUSH_COOLDOWN_MINUTES', '30',
        'Trong khoang nay mot tac gia chi bat push mot lan; bai sau van vao thong bao trong app (0-1440)', TRUE),
       ('SOCIAL', 'FOLLOWER_PUSH_DAILY_MAX', '10',
        'So thong bao bai cua nguoi minh theo doi toi da duoc push cho mot nguoi moi ngay (0-100)', TRUE),
       ('SOCIAL', 'FOLLOWER_FANOUT_BATCH_SIZE', '500',
        'So follower xu ly moi lo (50-500)', TRUE),
       ('SOCIAL', 'FOLLOWER_FANOUT_PAUSE_MS', '100',
        'Nghi giua hai lo de khong chiem DB (0-5000)', TRUE),
       ('SOCIAL', 'MOMENTS_WINDOW_HOURS', '72',
        'Popup chi hien bai chua xem trong khoang nay (1-336)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
