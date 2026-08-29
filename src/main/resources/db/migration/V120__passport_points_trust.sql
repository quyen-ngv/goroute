-- Epic 04 (Passport), epic 08 (shared point wallet, creator roles).

-- PAS-01. The single source of truth for "how many provinces are there" and for matching
-- the many ways a province is written. Free-text addresses cannot be counted: "Hà Nội",
-- "TP Hà Nội", "Ha Noi" and "Thành phố Hà Nội" are four strings and one province.
CREATE TABLE provinces (
    code            VARCHAR(10) PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    region          VARCHAR(40),
    -- Every spelling that should resolve to this province, already normalized.
    aliases         JSONB NOT NULL DEFAULT '[]'::jsonb,
    latitude        NUMERIC(10, 7),
    longitude       NUMERIC(10, 7),
    -- Which official list version this row belongs to, so two backfill runs cannot
    -- silently disagree.
    dataset_version VARCHAR(20) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_provinces_normalized ON provinces (normalized_name);

-- Province assignment for the existing catalogue. Ambiguous rows are marked, never
-- guessed: a wrong guess produces a stamp for a place the user has never been, and
-- taking a stamp back is worse than not granting it.
ALTER TABLE places ADD COLUMN IF NOT EXISTS province_code VARCHAR(10);
ALTER TABLE places ADD COLUMN IF NOT EXISTS province_status VARCHAR(20);
ALTER TABLE places ADD COLUMN IF NOT EXISTS province_confidence NUMERIC(4, 3);
ALTER TABLE places ADD COLUMN IF NOT EXISTS province_assigned_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_places_province ON places (province_code)
    WHERE province_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_places_province_review ON places (province_status)
    WHERE province_status = 'AMBIGUOUS';

-- PAS-02. Check-in is the action; a passport event is the consequence. Keeping them apart
-- means a third source (a location-verified review, say) can be added later without
-- touching either check-in table.
CREATE TABLE passport_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source         VARCHAR(30) NOT NULL,
    -- Identifier of the row that caused the event, unique per source: replaying the
    -- projection any number of times yields exactly one event.
    source_id      UUID NOT NULL,
    place_id       UUID REFERENCES places (id) ON DELETE SET NULL,
    checkin_id     UUID REFERENCES user_checkins (id) ON DELETE SET NULL,
    location_key   VARCHAR(40),
    location_name  VARCHAR(300),
    province_code  VARCHAR(10),
    occurred_at    TIMESTAMP NOT NULL,
    is_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    -- The user can hide an event from other people without losing it from their own
    -- history; hidden is not deleted.
    is_hidden      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_passport_event_source UNIQUE (source, source_id),
    CONSTRAINT ck_passport_event_source
        CHECK (source IN ('ACTIVITY_CHECKIN', 'USER_CHECKIN'))
);
CREATE INDEX idx_passport_events_user ON passport_events (user_id, occurred_at DESC);
CREATE INDEX idx_passport_events_province ON passport_events (user_id, province_code)
    WHERE province_code IS NOT NULL;

-- PAS-04. Award rules are data, not one code branch per rule, so a new rule is a row
-- rather than a release.
CREATE TABLE passport_stamp_rules (
    code            VARCHAR(60) NOT NULL,
    version         INT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    condition_type  VARCHAR(40) NOT NULL,
    threshold       INT NOT NULL DEFAULT 1,
    icon            VARCHAR(100),
    reward_points   INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (code, version),
    CONSTRAINT ck_stamp_condition CHECK (condition_type IN
        ('FIRST_CHECKIN', 'CHECKIN_COUNT', 'DISTINCT_PLACE_COUNT',
         'DISTINCT_PROVINCE_COUNT', 'VERIFIED_CHECKIN_COUNT'))
);

-- What was actually granted. Unique per (user, rule, version) so recomputation can never
-- award twice, and a rule change never rewrites what somebody already earned.
CREATE TABLE passport_stamps (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rule_code           VARCHAR(60) NOT NULL,
    rule_version        INT NOT NULL,
    triggering_event_id UUID REFERENCES passport_events (id) ON DELETE SET NULL,
    awarded_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_passport_stamp UNIQUE (user_id, rule_code, rule_version)
);
CREATE INDEX idx_passport_stamps_user ON passport_stamps (user_id, awarded_at DESC);

-- PAS-05. Wanting to go somewhere is the user's own data and is kept completely apart
-- from the event stream, so it can never be mistaken for having been there.
CREATE TABLE passport_province_wishes (
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    province_code VARCHAR(10) NOT NULL REFERENCES provinces (code) ON DELETE CASCADE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, province_code)
);

-- PAS-07. Reward catalogue and issued vouchers. Deliberately separate from marketplace
-- vouchers: two sources, two rule sets, and mixing them creates an accounting problem.
CREATE TABLE passport_rewards (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(60) NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    points_cost      INT NOT NULL CHECK (points_cost >= 0),
    required_stamp_code VARCHAR(60),
    total_quantity   INT,
    issued_quantity  INT NOT NULL DEFAULT 0,
    valid_days       INT NOT NULL DEFAULT 30,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_reward_quantity CHECK (total_quantity IS NULL OR issued_quantity <= total_quantity)
);

CREATE TABLE passport_reward_vouchers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reward_id      UUID NOT NULL REFERENCES passport_rewards (id) ON DELETE RESTRICT,
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code           VARCHAR(40) NOT NULL UNIQUE,
    status         VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    points_spent   INT NOT NULL,
    -- Ties the voucher to the wallet entry that paid for it, so a redemption retried by a
    -- flaky network produces one voucher and one debit.
    redemption_key VARCHAR(120) NOT NULL UNIQUE,
    expires_at     TIMESTAMP,
    used_at        TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_voucher_status CHECK (status IN ('ISSUED', 'USED', 'EXPIRED', 'REVOKED'))
);
CREATE INDEX idx_reward_vouchers_user ON passport_reward_vouchers (user_id, created_at DESC);

-- REWARD-01. The existing star wallet becomes the one point wallet. Refunds are reverse
-- entries, never edits, so the ledger stays reconcilable against the balance.
ALTER TABLE star_transactions ADD COLUMN IF NOT EXISTS balance_after INT;
ALTER TABLE star_transactions ADD COLUMN IF NOT EXISTS reverses_transaction_id UUID
    REFERENCES star_transactions (id) ON DELETE SET NULL;
ALTER TABLE star_transactions ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE star_transactions ADD COLUMN IF NOT EXISTS reason TEXT;

-- The idempotency guarantee the wallet already relied on, now enforced by the database
-- rather than by a count-then-insert that two concurrent requests can both pass.
--
-- Any duplicate that slipped through the old check has to be dealt with before the index
-- can exist. The keys are made unique rather than the rows deleted: each of those entries
-- did move the balance, so removing one would leave the ledger disagreeing with the
-- wallet -- exactly the condition this index exists to prevent. Renaming keeps both the
-- sum and the balance intact and leaves the anomaly visible.
UPDATE star_transactions t
SET reference_key = t.reference_key || ':dup:' || t.id
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY reference_key ORDER BY created_at, id) AS position
    FROM star_transactions
) ranked
WHERE ranked.id = t.id AND ranked.position > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_star_transaction_reference
    ON star_transactions (reference_key);
CREATE INDEX IF NOT EXISTS idx_star_transactions_user
    ON star_transactions (user_id, created_at DESC);

-- A balance may never go negative, in any code path, including an operator adjustment.
-- Added NOT VALID so an existing negative row cannot block the deployment: new writes are
-- checked from this moment, and any historical anomaly is surfaced by the reconciliation
-- query rather than by a failed migration nobody can act on at 3am.
ALTER TABLE user_star_wallets ADD CONSTRAINT ck_wallet_balance_not_negative
    CHECK (balance >= 0) NOT VALID;

-- TRUST-02. Rank is automatic and derived from activity; a role is granted by a person.
-- They have different lifecycles, so they are different data.
CREATE TABLE user_trust_roles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role             VARCHAR(30) NOT NULL,
    -- A local expert is an expert somewhere. "Expert" on its own means nothing.
    area_province_code VARCHAR(10) REFERENCES provinces (code) ON DELETE SET NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    application_note TEXT,
    decision_note    TEXT,
    decided_by       UUID,
    decided_at       TIMESTAMP,
    granted_at       TIMESTAMP,
    review_due_at    TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_trust_role CHECK (role IN ('CREATOR', 'KOL', 'KOC', 'LOCAL_EXPERT')),
    CONSTRAINT ck_trust_role_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED')),
    CONSTRAINT uq_trust_role_per_area UNIQUE (user_id, role, area_province_code)
);
CREATE INDEX idx_trust_roles_queue ON user_trust_roles (status, created_at);
CREATE INDEX idx_trust_roles_user ON user_trust_roles (user_id) WHERE status = 'APPROVED';
