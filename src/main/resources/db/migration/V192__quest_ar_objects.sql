-- AR objects (§3.15): a checkpoint can be cleared by finding a 3D object in AR and tapping it.
--   * quest_ar_object_assets  the library of 3D objects the console manages (GLB for Android,
--                             USDZ for iOS, a thumbnail). Creators pick from it; they never upload 3D.
--   * quest_checkpoints.ar_object  where the object stands and how it is anchored, as one JSON
--                             document (the checkpoint graph is rewritten whole on every save).
--   * quest_run_checkpoints.ar_*   the tap that cleared it, kept for the replay check.
--   * quest_run_members.ar_supported  whether the member's app can show AR. An app without it
--                             plays an AR_OBJECT checkpoint as ARRIVE, so it is never stuck.
-- Additive, forward-only. No physical foreign keys, per project convention.

CREATE TABLE IF NOT EXISTS quest_ar_object_assets (
    id             UUID PRIMARY KEY,
    name           VARCHAR(120)  NOT NULL,
    description    TEXT          NULL,
    glb_url        TEXT          NOT NULL,
    glb_bytes      BIGINT        NULL,
    usdz_url       TEXT          NULL,
    usdz_bytes     BIGINT        NULL,
    thumbnail_url  TEXT          NULL,
    -- Real-world height of the model in metres, as authored (the app shows it at this size).
    height_m       NUMERIC(6,3)  NULL,
    -- The animation clips found in the GLB: [{"name":"idle","seconds":2.0}, ...]. A USDZ holds
    -- one timeline, so on iOS the clips are cut from it in this order.
    clips          JSONB         NOT NULL DEFAULT '[]'::jsonb,
    triangles      INT           NULL,
    -- Only an asset with a `walk` clip may be used for a WANDER object.
    can_wander     BOOLEAN       NOT NULL DEFAULT FALSE,
    tags           JSONB         NOT NULL DEFAULT '[]'::jsonb,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by     UUID          NULL,
    data_version   BIGINT        NOT NULL DEFAULT 1,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quest_ar_object_assets_active ON quest_ar_object_assets (is_active, name);

ALTER TABLE quest_checkpoints ADD COLUMN IF NOT EXISTS ar_object JSONB NULL;
ALTER TABLE quest_checkpoints DROP CONSTRAINT IF EXISTS ck_quest_checkpoint_completion_mode;
ALTER TABLE quest_checkpoints
    ADD CONSTRAINT ck_quest_checkpoint_completion_mode
        CHECK (completion_mode IN ('TASK', 'ARRIVE', 'STOPS', 'AR_OBJECT'));

ALTER TABLE quest_run_checkpoints
    ADD COLUMN IF NOT EXISTS ar_tapped_at    TIMESTAMP     NULL,
    ADD COLUMN IF NOT EXISTS ar_tap_lat      NUMERIC(10,7) NULL,
    ADD COLUMN IF NOT EXISTS ar_tap_lng      NUMERIC(10,7) NULL,
    ADD COLUMN IF NOT EXISTS ar_tap_accuracy NUMERIC(8,2)  NULL,
    -- APPROX, IMAGE, or FALLBACK (the device could not run AR and showed the 3D view instead).
    ADD COLUMN IF NOT EXISTS ar_anchor_mode  VARCHAR(10)   NULL;

ALTER TABLE quest_run_members
    ADD COLUMN IF NOT EXISTS ar_supported BOOLEAN NOT NULL DEFAULT FALSE;
