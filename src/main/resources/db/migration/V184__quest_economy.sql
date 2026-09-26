-- Quest platform, phase 8: the economy (§3.5, D19, D20). Two tables, deliberately separate from
-- the Stars wallet: creator income lives in quest_earnings, which the year-end Stars reset
-- (StarYearEndResetJob) does NOT touch (D20/trap #6). A creator moves earnings into their wallet
-- when they want to spend, and accepts the reset from that moment.

CREATE TABLE IF NOT EXISTS quest_earnings (
    id              UUID PRIMARY KEY,
    creator_id      UUID        NOT NULL,
    quest_id        UUID        NULL,
    run_id          UUID        NULL,
    from_user_id    UUID        NULL,
    source          VARCHAR(20) NOT NULL,
    amount          INT         NOT NULL,
    funding_source  VARCHAR(20) NOT NULL DEFAULT 'STARS',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- Held until this instant before it can be withdrawn (EARNING_SETTLE_DAYS), a brake on
    -- Stars laundering through a high-priced quest (§3.5 risk table).
    settle_at       TIMESTAMP   NULL,
    -- Idempotency: the payment that produced this credit. UNIQUE so a retried unlock/tip
    -- cannot double-credit the creator.
    source_reference VARCHAR(120) NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_earning_source CHECK (source IN ('SALE', 'TIP')),
    CONSTRAINT ck_quest_earning_funding CHECK (funding_source IN ('STARS', 'CASH')),
    CONSTRAINT ck_quest_earning_status CHECK (status IN ('PENDING', 'SETTLED', 'WITHDRAWN')),
    CONSTRAINT ck_quest_earning_amount CHECK (amount > 0),
    CONSTRAINT uq_quest_earning_reference UNIQUE (source_reference)
);
CREATE INDEX IF NOT EXISTS idx_quest_earnings_creator ON quest_earnings (creator_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS quest_tips (
    id                UUID PRIMARY KEY,
    quest_id          UUID        NOT NULL,
    run_id            UUID        NULL,
    from_user_id      UUID        NOT NULL,
    to_creator_id     UUID        NOT NULL,
    amount            INT         NOT NULL,
    funding_source    VARCHAR(20) NOT NULL DEFAULT 'STARS',
    message           TEXT        NULL,
    is_message_public BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_quest_tip_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_quest_tips_creator ON quest_tips (to_creator_id, created_at DESC);
