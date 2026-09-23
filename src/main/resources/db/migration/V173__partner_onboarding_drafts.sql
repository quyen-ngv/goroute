-- Partner onboarding: the draft a partner builds a listing in, one step at a time.
--
-- The wizard exists because a listing is not one form. A stay needs a place, a room, a rate,
-- a calendar and a cancellation policy before it can be sold, and asking for all of that on
-- one screen is how partners abandon halfway. So the answers are collected step by step and
-- parked here until they are complete; only `submit` turns them into real marketplace rows
-- through the same partner services the console already uses.
--
-- This table is deliberately the only schema this feature adds. It owns nothing the
-- marketplace owns: no listing, no price, no inventory. Deleting it would lose unfinished
-- drafts and nothing else.

CREATE TABLE IF NOT EXISTS partner_onboarding_drafts (
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL,
    -- Null until the organization step runs. Media upload and every partner endpoint need an
    -- organization, which is why that step comes first in every branch.
    organization_id    UUID        NULL,
    listing_kind       VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_step       VARCHAR(60) NULL,
    -- Step codes already answered, in the order they were completed.
    completed_steps    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    -- { "<stepCode>": { ...answers } }. The server stores and returns it; the step shape is
    -- owned by the clients and validated for real by the marketplace DTOs at submit time.
    data               JSONB       NOT NULL DEFAULT '{}'::jsonb,
    result_hotel_id    UUID        NULL,
    result_activity_id UUID        NULL,
    submitted_at       TIMESTAMP   NULL,
    data_version       BIGINT      NOT NULL DEFAULT 1,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_onboarding_draft_kind CHECK (listing_kind IN ('STAY', 'EXPERIENCE', 'SERVICE')),
    CONSTRAINT chk_onboarding_draft_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'ABANDONED')),
    -- A submitted draft always points at exactly one listing; an unsubmitted one at none.
    CONSTRAINT chk_onboarding_draft_result CHECK (
        (status <> 'SUBMITTED' AND result_hotel_id IS NULL AND result_activity_id IS NULL)
        OR (status = 'SUBMITTED' AND (result_hotel_id IS NULL) <> (result_activity_id IS NULL))
    )
);

-- "What am I still working on?" — the only list query the app and console make.
CREATE INDEX IF NOT EXISTS idx_onboarding_drafts_user
    ON partner_onboarding_drafts (user_id, status, updated_at DESC);

-- A colleague with HOTEL_WRITE/ACTIVITY_WRITE may resume a draft of their organization.
CREATE INDEX IF NOT EXISTS idx_onboarding_drafts_organization
    ON partner_onboarding_drafts (organization_id, status, updated_at DESC)
    WHERE organization_id IS NOT NULL;
