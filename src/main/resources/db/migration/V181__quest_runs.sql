-- Quest platform, phase 3: play-through state (§6.2). A run pins a version (D11); everything a
-- player does is recorded per member (group play, §3.13) so one person walking cannot reward the
-- whole group. No physical foreign keys, per convention.

CREATE TABLE IF NOT EXISTS quest_runs (
    id                UUID PRIMARY KEY,
    quest_id          UUID        NOT NULL,
    -- The immutable snapshot this run plays against.
    quest_version_id  UUID        NOT NULL,
    owner_user_id     UUID        NOT NULL,
    trip_id           UUID        NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_activity_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP   NULL,
    completed_at      TIMESTAMP   NULL,
    rank_score        INT         NULL,
    data_version      BIGINT      NOT NULL DEFAULT 1,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_run_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'EXPIRED', 'TERMINATED'))
);
-- One open run per person per quest (§6.2). Partial unique on the only non-terminal status.
CREATE UNIQUE INDEX IF NOT EXISTS uq_quest_run_open
    ON quest_runs (quest_id, owner_user_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX IF NOT EXISTS idx_quest_runs_owner ON quest_runs (owner_user_id, status, last_activity_at DESC);
CREATE INDEX IF NOT EXISTS idx_quest_runs_quest ON quest_runs (quest_id, status);

CREATE TABLE IF NOT EXISTS quest_run_members (
    id                   UUID PRIMARY KEY,
    run_id               UUID        NOT NULL,
    user_id              UUID        NOT NULL,
    joined_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    left_at              TIMESTAMP   NULL,
    -- Verification lives on the member, not the run: in a group each person is proven separately.
    verification         VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    presence_checkpoints INT         NOT NULL DEFAULT 0,
    rewarded             BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_quest_run_member_verification CHECK (verification IN ('VERIFIED', 'UNVERIFIED')),
    CONSTRAINT uq_quest_run_member UNIQUE (run_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_quest_run_members_run ON quest_run_members (run_id);

CREATE TABLE IF NOT EXISTS quest_run_checkpoints (
    id               UUID PRIMARY KEY,
    run_id           UUID        NOT NULL,
    member_id        UUID        NOT NULL,
    checkpoint_id    UUID        NOT NULL,
    -- Arrival is decided by the server from a stable streak of samples (§3.8), not the client.
    gps_override     BOOLEAN     NOT NULL DEFAULT FALSE,
    distance_meters  NUMERIC(10,2) NULL,
    accuracy_meters  NUMERIC(10,2) NULL,
    stable_streak    INT         NOT NULL DEFAULT 0,
    last_sample_at   TIMESTAMP   NULL,
    last_sample_lat  NUMERIC(10,7) NULL,
    last_sample_lng  NUMERIC(10,7) NULL,
    last_sample_id   UUID        NULL,
    unlocked_at      TIMESTAMP   NULL,
    -- Points at the user_checkins row when the checkpoint required a check-in; the state tracks
    -- what happened to it afterwards without ever reversing the run (§6.2).
    checkin_id       UUID        NULL,
    checkin_state    VARCHAR(20) NULL,
    -- Concurrent client writes are normal in a group, so this row has its own optimistic lock.
    data_version     BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT ck_quest_run_checkpoint_checkin_state
        CHECK (checkin_state IS NULL OR checkin_state IN
            ('WAIVED', 'PENDING_UPLOAD', 'ACTIVE', 'HIDDEN', 'DELETED')),
    CONSTRAINT uq_quest_run_checkpoint UNIQUE (member_id, checkpoint_id)
);
CREATE INDEX IF NOT EXISTS idx_quest_run_checkpoints_run ON quest_run_checkpoints (run_id);

CREATE TABLE IF NOT EXISTS quest_run_questions (
    id              UUID PRIMARY KEY,
    run_id          UUID        NOT NULL,
    member_id       UUID        NOT NULL,
    question_id     UUID        NOT NULL,
    guess_count     INT         NOT NULL DEFAULT 0,
    hint_tier_bought INT        NOT NULL DEFAULT 0,
    revealed        BOOLEAN     NOT NULL DEFAULT FALSE,
    skipped         BOOLEAN     NOT NULL DEFAULT FALSE,
    correct         BOOLEAN     NOT NULL DEFAULT FALSE,
    answered_at     TIMESTAMP   NULL,
    CONSTRAINT uq_quest_run_question UNIQUE (member_id, question_id)
);
CREATE INDEX IF NOT EXISTS idx_quest_run_questions_run ON quest_run_questions (run_id);

-- Proof of ownership (§6.2 D19): who unlocked which quest, and how it was paid for. Distinct from
-- the Stars transaction, which is only the payment.
CREATE TABLE IF NOT EXISTS quest_entitlements (
    id               UUID PRIMARY KEY,
    quest_id         UUID        NOT NULL,
    user_id          UUID        NOT NULL,
    funding_source   VARCHAR(20) NOT NULL DEFAULT 'STARS',
    source_reference VARCHAR(120) NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_entitlement_funding CHECK (funding_source IN ('STARS', 'CASH', 'FREE')),
    CONSTRAINT uq_quest_entitlement UNIQUE (quest_id, user_id)
);

-- Raw location samples, retained for LOCATION_SAMPLE_RETENTION_DAYS then purged (§7.6). Reading
-- this table needs quests:location-data and is audited.
CREATE TABLE IF NOT EXISTS quest_location_samples (
    id              UUID PRIMARY KEY,
    run_id          UUID        NOT NULL,
    member_id       UUID        NOT NULL,
    checkpoint_id   UUID        NULL,
    latitude        NUMERIC(10,7) NOT NULL,
    longitude       NUMERIC(10,7) NOT NULL,
    accuracy_meters NUMERIC(10,2) NULL,
    captured_at     TIMESTAMP   NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quest_location_samples_run ON quest_location_samples (run_id, captured_at);
CREATE INDEX IF NOT EXISTS idx_quest_location_samples_purge ON quest_location_samples (created_at);
