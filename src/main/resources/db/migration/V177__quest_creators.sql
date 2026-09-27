-- Quest platform, foundation: the creator profile.
--
-- A profile, not a licence (D4). It is created the first time a user taps "create quest";
-- holding a row here grants nothing by itself. What it carries is the accountability the
-- review path leans on now that no human vets the author up front (§3.2): the signed terms
-- (terms_accepted_at + terms_version, §7.5), a quality score that widens limits for good
-- creators and tightens them for bad ones, and the ACTIVE/SUSPENDED switch that takes a
-- creator's quests out of discovery.
--
-- No physical foreign keys, per the project convention: user_id and organization_id are
-- logical references (organization_id → host_organizations, nullable — most creators are
-- individuals). This is the only table the foundation phase adds; quests, versions,
-- checkpoints and runs arrive with the builder.

CREATE TABLE IF NOT EXISTS quest_creators (
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL,
    -- Set when the creator builds on behalf of a partner organization; the public gate (§6.3)
    -- then also checks that organization is enabled and verified.
    organization_id    UUID        NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- The signature is the only binding on a creator once §3.2 dropped the up-front human
    -- review, so both are recorded: when they accepted, and which version they accepted.
    terms_accepted_at  TIMESTAMP   NULL,
    terms_version      VARCHAR(20) NULL,
    -- Adjusts submission limits and review priority (§3.2). Starts neutral.
    quality_score      INT         NOT NULL DEFAULT 0,
    data_version       BIGINT      NOT NULL DEFAULT 1,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- One profile per user. A partner colleague and their own account are two users, two rows.
    CONSTRAINT uq_quest_creator_user UNIQUE (user_id),
    CONSTRAINT ck_quest_creator_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- The console lists and filters creators by state; the public gate joins by id.
CREATE INDEX IF NOT EXISTS idx_quest_creators_status
    ON quest_creators (status, updated_at DESC);

-- A partner organization's creators, for the console's partner view.
CREATE INDEX IF NOT EXISTS idx_quest_creators_organization
    ON quest_creators (organization_id)
    WHERE organization_id IS NOT NULL;
