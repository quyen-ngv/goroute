-- Quest platform §3.14: a checkpoint is no longer only "walk to the pin, then do a task". It has
-- three independent settings, and every way of playing is a combination of them:
--   * find_mode       PIN (the player sees the pin) or AREA (a search circle offset from the real
--                     spot, finding clues to buy, optional hot/cold);
--   * completion_mode TASK (D21, as before), ARRIVE (guide only: unlocking is enough) or STOPS
--                     (unlocking plus min_stops storytelling points heard by GPS);
--   * content         the story, the creator's recording of it, and storytelling points.
-- Existing rows keep PIN + TASK, i.e. exactly how they played before.
-- Additive, forward-only. No physical foreign keys, per project convention.

ALTER TABLE quest_checkpoints
    ADD COLUMN IF NOT EXISTS find_mode           VARCHAR(16)  NOT NULL DEFAULT 'PIN',
    -- AREA only: the search circle. Its centre is computed by the server on save (seeded, so an
    -- edit that does not move the spot does not move the circle) and always contains the spot.
    ADD COLUMN IF NOT EXISTS search_radius_m     INT          NULL,
    ADD COLUMN IF NOT EXISTS search_center_lat   NUMERIC(9,6) NULL,
    ADD COLUMN IF NOT EXISTS search_center_lng   NUMERIC(9,6) NULL,
    ADD COLUMN IF NOT EXISTS hot_cold_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS completion_mode     VARCHAR(16)  NOT NULL DEFAULT 'TASK',
    ADD COLUMN IF NOT EXISTS min_stops           INT          NULL,
    -- The creator's own recording of `story`, in the version's content_language.
    ADD COLUMN IF NOT EXISTS story_audio_url     TEXT         NULL,
    ADD COLUMN IF NOT EXISTS story_audio_seconds INT          NULL;

ALTER TABLE quest_checkpoints
    ADD CONSTRAINT ck_quest_checkpoint_find_mode CHECK (find_mode IN ('PIN', 'AREA')),
    ADD CONSTRAINT ck_quest_checkpoint_completion_mode CHECK (completion_mode IN ('TASK', 'ARRIVE', 'STOPS')),
    ADD CONSTRAINT ck_quest_checkpoint_search_radius
        CHECK (search_radius_m IS NULL OR search_radius_m BETWEEN 50 AND 1000),
    ADD CONSTRAINT ck_quest_checkpoint_min_stops CHECK (min_stops IS NULL OR min_stops >= 1),
    ADD CONSTRAINT ck_quest_checkpoint_story_audio_seconds
        CHECK (story_audio_seconds IS NULL OR story_audio_seconds >= 0);

-- Finding clues of an AREA checkpoint, bought in tier order. The price is the creator's.
CREATE TABLE IF NOT EXISTS quest_checkpoint_clues (
    id            UUID PRIMARY KEY,
    checkpoint_id UUID        NOT NULL,
    tier          SMALLINT    NOT NULL,
    kind          VARCHAR(10) NOT NULL,
    text          TEXT        NULL,
    image_url     TEXT        NULL,
    cost_stars    INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_clue_tier CHECK (tier BETWEEN 1 AND 3),
    CONSTRAINT ck_quest_clue_kind CHECK (kind IN ('TEXT', 'PHOTO', 'REVEAL')),
    CONSTRAINT ck_quest_clue_cost CHECK (cost_stars >= 0),
    CONSTRAINT uq_quest_clue_tier UNIQUE (checkpoint_id, tier)
);

-- Storytelling points inside a checkpoint. The coordinates are where the listener stands, not
-- where the object is. Sent to the app only once the checkpoint is unlocked.
CREATE TABLE IF NOT EXISTS quest_checkpoint_stops (
    id            UUID PRIMARY KEY,
    checkpoint_id UUID          NOT NULL,
    sort_order    INT           NOT NULL,
    name          VARCHAR(200)  NULL,
    category      VARCHAR(32)   NULL,
    latitude      NUMERIC(10,7) NOT NULL,
    longitude     NUMERIC(10,7) NOT NULL,
    radius_m      INT           NOT NULL DEFAULT 30,
    story         TEXT          NULL,
    image_urls    JSONB         NOT NULL DEFAULT '[]'::jsonb,
    audio_url     TEXT          NULL,
    audio_seconds INT           NULL,
    place_id      UUID          NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_stop_radius CHECK (radius_m BETWEEN 10 AND 100),
    CONSTRAINT ck_quest_stop_audio_seconds CHECK (audio_seconds IS NULL OR audio_seconds >= 0)
);
CREATE INDEX IF NOT EXISTS idx_quest_checkpoint_clues_checkpoint ON quest_checkpoint_clues (checkpoint_id, tier);
CREATE INDEX IF NOT EXISTS idx_quest_checkpoint_stops_checkpoint ON quest_checkpoint_stops (checkpoint_id, sort_order);

-- A clue bought by one member. Unique per (member, checkpoint, tier): buying is idempotent.
CREATE TABLE IF NOT EXISTS quest_run_clues (
    id                  UUID PRIMARY KEY,
    run_id              UUID      NOT NULL,
    member_id           UUID      NOT NULL,
    checkpoint_id       UUID      NOT NULL,
    tier                SMALLINT  NOT NULL,
    stars_spent         INT       NOT NULL DEFAULT 0,
    star_transaction_id UUID      NULL,
    bought_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_quest_run_clue UNIQUE (member_id, checkpoint_id, tier)
);
CREATE INDEX IF NOT EXISTS idx_quest_run_clues_member ON quest_run_clues (member_id, checkpoint_id);

-- A storytelling point heard by one member. A TAP is recorded but never counts toward STOPS
-- completion (it can be done from home); a later GPS visit upgrades the row.
CREATE TABLE IF NOT EXISTS quest_run_stop_visits (
    id                       UUID PRIMARY KEY,
    run_id                   UUID        NOT NULL,
    member_id                UUID        NOT NULL,
    checkpoint_id            UUID        NOT NULL,
    stop_id                  UUID        NOT NULL,
    via                      VARCHAR(8)  NOT NULL,
    counts_toward_completion BOOLEAN     NOT NULL DEFAULT FALSE,
    visited_at               TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_run_stop_visit_via CHECK (via IN ('GPS', 'TAP')),
    CONSTRAINT uq_quest_run_stop_visit UNIQUE (member_id, stop_id)
);
CREATE INDEX IF NOT EXISTS idx_quest_run_stop_visits_member ON quest_run_stop_visits (member_id, checkpoint_id);

-- Hot/cold: the last distance to the real spot (never sent to the app) and the band it gave, so
-- the trend can be computed and a question inside the minimum interval gets the same answer.
ALTER TABLE quest_run_checkpoints
    ADD COLUMN IF NOT EXISTS last_proximity_m  NUMERIC(10,2) NULL,
    ADD COLUMN IF NOT EXISTS last_proximity_at TIMESTAMP     NULL,
    ADD COLUMN IF NOT EXISTS proximity_band    VARCHAR(8)    NULL,
    ADD COLUMN IF NOT EXISTS proximity_trend   VARCHAR(8)    NULL;

-- The creator earns a share of every finding clue a player buys, like a sale.
ALTER TABLE quest_earnings DROP CONSTRAINT IF EXISTS ck_quest_earning_source;
ALTER TABLE quest_earnings
    ADD CONSTRAINT ck_quest_earning_source CHECK (source IN ('SALE', 'TIP', 'CLUE'));
