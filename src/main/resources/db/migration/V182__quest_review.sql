-- Quest platform, phase 4: the review audit (§6.2, §3.3). The decision to publish IS the review
-- (§3.1), so a decision row is written every time a reviewer publishes, denies or sends a quest to
-- field test. `self_approved` records the one sanctioned exception (SUPER_ADMIN + origin SYSTEM,
-- §3.3 layer 2) so it can be counted even though it cannot be blocked.

CREATE TABLE IF NOT EXISTS quest_review_decisions (
    id                UUID PRIMARY KEY,
    quest_id          UUID        NOT NULL,
    quest_version_id  UUID        NOT NULL,
    reviewer_user_id  UUID        NOT NULL,
    decision          VARCHAR(20) NOT NULL,
    reason            TEXT        NULL,
    -- The checklist the reviewer ticked (§7.2), kept for audit.
    checklist         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    self_approved     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_review_decision CHECK (decision IN ('PUBLISHED', 'DENIED', 'FIELD_TEST'))
);
CREATE INDEX IF NOT EXISTS idx_quest_review_decisions_quest
    ON quest_review_decisions (quest_id, created_at DESC);
-- The self-approval audit tab (§3.3 layer 2) reads this partial index.
CREATE INDEX IF NOT EXISTS idx_quest_review_decisions_self_approved
    ON quest_review_decisions (created_at DESC) WHERE self_approved = TRUE;

CREATE TABLE IF NOT EXISTS quest_review_comments (
    id                UUID PRIMARY KEY,
    quest_id          UUID        NOT NULL,
    quest_version_id  UUID        NOT NULL,
    -- Null for a quest-level note; set for a per-checkpoint note the creator sees on denial.
    checkpoint_id     UUID        NULL,
    reviewer_user_id  UUID        NOT NULL,
    comment           TEXT        NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quest_review_comments_quest
    ON quest_review_comments (quest_id, created_at);
