-- Quest platform, phase 2: the builder schema — the root, its immutable versions, and the
-- content a version owns (checkpoints, questions, choices, private notes).
--
-- Shape follows the plan (§6.2, §6.2.1, §3.12):
--   * `quests` holds nothing a player sees. It is the stable identity plus two pointers:
--     `published_version_id` (the live version) and `draft_version_id` (the one being edited).
--     `data_version` is the optimistic lock the builder sends back; it is NOT the content
--     `version`.
--   * Every edit mints a new `quest_versions` row and never rewrites an old one. A run pins a
--     version id, so that row must be immutable for the run's snapshot to be real.
--   * Checkpoints carry their own coordinates (D2); `place_id` is optional and, once published,
--     the runtime reads `unlock_geometry`/`radius_m` off the checkpoint, never `places` (§6.2.1).
--   * Answers live here in plain text (the reviewer reads them, the player can buy a reveal),
--     but never leave through a public DTO (§6.2.2, D6) — enforced in the service/DTO layer.
-- No physical foreign keys, per project convention.

CREATE TABLE IF NOT EXISTS quests (
    id                    UUID PRIMARY KEY,
    creator_id            UUID        NOT NULL,
    -- Fixed at creation, never changed (D16).
    origin                VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_version_id  UUID        NULL,
    draft_version_id      UUID        NULL,
    -- The post-review flag: a MINOR edit went live and is waiting to be looked at (§3.10, D18).
    pending_change_review BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Translations of one quest share a group; each language is its own quest (§6.2).
    translation_group_id  UUID        NULL,
    -- Who paused it, so a creator may resume only their own pause, never an auto-pause (§3.1).
    paused_by             VARCHAR(10) NULL,
    -- Set when a SUPER_ADMIN self-approves a SYSTEM quest (§3.3 layer 2); surfaced in audit.
    self_approved         BOOLEAN     NOT NULL DEFAULT FALSE,
    data_version          BIGINT      NOT NULL DEFAULT 1,
    is_deleted            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_origin CHECK (origin IN ('SYSTEM', 'PARTNER', 'COMMUNITY')),
    CONSTRAINT ck_quest_status CHECK (status IN (
        'DRAFT', 'PENDING', 'IN_REVIEW', 'FIELD_TEST', 'PUBLISHED',
        'DENIED', 'PAUSED', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT ck_quest_paused_by CHECK (paused_by IS NULL OR paused_by IN ('CREATOR', 'ADMIN', 'SYSTEM'))
);

CREATE INDEX IF NOT EXISTS idx_quests_creator ON quests (creator_id, status, updated_at DESC);
-- Discovery reads only PUBLISHED, live rows.
CREATE INDEX IF NOT EXISTS idx_quests_published
    ON quests (updated_at DESC)
    WHERE status = 'PUBLISHED' AND is_deleted = FALSE AND published_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_quests_translation_group
    ON quests (translation_group_id) WHERE translation_group_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS quest_versions (
    id                UUID PRIMARY KEY,
    quest_id          UUID        NOT NULL,
    -- version bumps on a material change; content_revision bumps on a minor one (§6.2.1).
    version           INT         NOT NULL DEFAULT 1,
    content_revision  INT         NOT NULL DEFAULT 0,
    change_kind       VARCHAR(10) NOT NULL DEFAULT 'MATERIAL',
    -- Everything a player sees and everything a reviewer must read:
    title             VARCHAR(200) NULL,
    summary           TEXT         NULL,
    description       TEXT         NULL,
    cover_media_id    UUID         NULL,
    difficulty        SMALLINT     NULL,
    estimated_minutes INT          NULL,
    distance_meters   INT          NULL,
    amenity_tags      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    safety_notes      TEXT         NULL,
    province_code     VARCHAR(10)  NULL,
    ward_code         VARCHAR(20)  NULL,
    content_language  VARCHAR(10)  NOT NULL DEFAULT 'vi',
    -- price_stars must live on the version, not on `quests`: §3.10 makes a price change material,
    -- and a price on `quests` would take effect immediately, bypassing the very gate §3.5 leans on.
    price_stars       INT          NOT NULL DEFAULT 0,
    reward_stars      INT          NOT NULL DEFAULT 0,
    playable_months   JSONB        NULL,
    playable_hours    JSONB        NULL,
    run_expiry_hours  INT          NULL,
    created_by        UUID         NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_version_change_kind CHECK (change_kind IN ('MATERIAL', 'MINOR')),
    CONSTRAINT ck_quest_version_difficulty CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_quest_version_price CHECK (price_stars >= 0),
    CONSTRAINT ck_quest_version_reward CHECK (reward_stars >= 0)
);
CREATE INDEX IF NOT EXISTS idx_quest_versions_quest ON quest_versions (quest_id, version DESC, content_revision DESC);

CREATE TABLE IF NOT EXISTS quest_checkpoints (
    id                      UUID PRIMARY KEY,
    quest_version_id        UUID          NOT NULL,
    sort_order              INT           NOT NULL,
    name                    VARCHAR(200)  NULL,
    -- NUMERIC(10,7) to match user_checkins, because CheckinVerifier and LocationKeyFactory
    -- work in that type; NOT places' DECIMAL(10,8).
    latitude                NUMERIC(10,7) NULL,
    longitude               NUMERIC(10,7) NULL,
    radius_m                INT           NULL,
    -- Frozen copy of the source place's polygon at publish time (§6.2.1); after publish the
    -- runtime reads this, never `places`.
    unlock_geometry         geometry(MultiPolygon, 4326) NULL,
    -- Kept only to trace provenance and let the health job reconcile; not read at runtime post-publish.
    place_id                UUID          NULL,
    location_key            VARCHAR(64)   NULL,
    -- The story unlocked after the checkpoint is solved.
    story                   TEXT          NULL,
    capture_source          VARCHAR(10)   NOT NULL DEFAULT 'MAP',
    capture_accuracy_meters NUMERIC(10,2) NULL,
    captured_at             TIMESTAMP     NULL,
    -- A checkpoint may require the player to check in (D13); a checkpoint with no required
    -- question must (D21) — enforced in the service, which reads both this and quest_questions.
    requires_checkin        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_checkpoint_capture CHECK (capture_source IN ('MAP', 'FIELD')),
    CONSTRAINT ck_quest_checkpoint_radius CHECK (radius_m IS NULL OR radius_m BETWEEN 25 AND 150)
);
CREATE INDEX IF NOT EXISTS idx_quest_checkpoints_version ON quest_checkpoints (quest_version_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_quest_checkpoints_place ON quest_checkpoints (place_id) WHERE place_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS quest_questions (
    id               UUID PRIMARY KEY,
    checkpoint_id    UUID          NOT NULL,
    sort_order       INT           NOT NULL,
    is_required      BOOLEAN       NOT NULL DEFAULT TRUE,
    is_bonus         BOOLEAN       NOT NULL DEFAULT FALSE,
    type             VARCHAR(20)   NOT NULL,
    prompt           TEXT          NULL,
    image_media_id   UUID          NULL,
    -- Server-only. These four never appear in a public DTO (§6.2.2, D6).
    answer_plain     TEXT          NULL,
    answer_variants  JSONB         NULL,
    number_tolerance NUMERIC(14,4) NULL,
    hint_tier1       TEXT          NULL,
    hint_tier2       TEXT          NULL,
    hint_tier3       TEXT          NULL,
    bonus_stars      INT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_question_type CHECK (type IN ('TEXT', 'NUMBER', 'CHOICE', 'MULTI_CHOICE', 'PHOTO')),
    CONSTRAINT ck_quest_question_bonus_stars CHECK (bonus_stars >= 0)
);
CREATE INDEX IF NOT EXISTS idx_quest_questions_checkpoint ON quest_questions (checkpoint_id, sort_order);

CREATE TABLE IF NOT EXISTS quest_question_choices (
    id          UUID PRIMARY KEY,
    question_id UUID    NOT NULL,
    sort_order  INT     NOT NULL,
    content     TEXT    NULL,
    -- Answers are stored by this stable id, never by index, because order is shuffled each run.
    is_correct  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_quest_choices_question ON quest_question_choices (question_id, sort_order);

CREATE TABLE IF NOT EXISTS quest_creator_notes (
    id            UUID PRIMARY KEY,
    checkpoint_id UUID      NOT NULL,
    note          TEXT      NULL,
    created_by    UUID      NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quest_creator_notes_checkpoint ON quest_creator_notes (checkpoint_id);
