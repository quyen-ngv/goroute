-- Credits are the one thing a user spends (30/10/2026, .docs/CREDIT_UNIFICATION_PLAN_2026-10-30.md).
-- Creating a trip costs AI_CREDIT.TRIP_CREATION_COST credits instead of a free slot or a slot bought
-- with 10 Stars, and Stars are exchanged one way into credits.

-- ---------------------------------------------------------------------------
-- 1. A third bucket. earned_balance holds credits exchanged from Stars and the one-off grant below:
--    it never expires and no refill touches it. Debits take plan -> earned -> purchased.
ALTER TABLE ai_credit_wallets
    ADD COLUMN IF NOT EXISTS earned_balance NUMERIC(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE ai_credit_wallets
    ADD CONSTRAINT chk_ai_credit_wallet_earned CHECK (earned_balance >= 0);

-- Rows written before this migration moved nothing in the earned bucket, so 0 is their true value.
ALTER TABLE ai_credit_transactions
    ADD COLUMN IF NOT EXISTS earned_amount        NUMERIC(18, 4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS earned_balance_after NUMERIC(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE ai_credit_transactions DROP CONSTRAINT IF EXISTS chk_ai_credit_transaction_type;
ALTER TABLE ai_credit_transactions
    ADD CONSTRAINT chk_ai_credit_transaction_type CHECK (transaction_type IN (
        'PLAN_REFILL', 'CHARGE', 'REFUND', 'PURCHASE', 'ADJUST',
        'TRIP_CREATE', 'STAR_EXCHANGE', 'MIGRATION_GRANT'));

-- LEGACY-STAR-QUOTA: marks a bought slot that was turned into credits below. Dropped together with
-- the table once no app build older than the first credit build is allowed (APP.MIN_BUILD_*).
ALTER TABLE trip_creation_entitlements
    ADD COLUMN IF NOT EXISTS converted_at TIMESTAMP;

INSERT INTO config (label, key, value, description, is_active)
VALUES ('AI_CREDIT', 'TRIP_CREATION_COST', '50',
        'So credit tru moi lan tao chuyen di: thu cong, clone, AI, Pengoo (0-100000)', TRUE),
       ('AI_CREDIT', 'STAR_EXCHANGE_ENABLED', 'true',
        'Cho phep doi Sao sang credit', TRUE),
       ('AI_CREDIT', 'STAR_EXCHANGE_RATE', '10',
        'So credit nhan duoc cho moi Sao (1-1000)', TRUE),
       ('AI_CREDIT', 'STAR_EXCHANGE_MONTHLY_CAP', '500',
        'So credit toi da doi tu Sao trong mot ky cua vi credit (0-100000)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. One-off: every unused free trip (TRIP/FREE_TRIP_QUOTA or the user's override, minus what was
--    used) and every bought slot still live becomes 200 credits in the earned bucket. Done here
--    rather than lazily, so no code keeps reading the old quota. New accounts get nothing extra:
--    the monthly plan credits cover their first trips.
CREATE TEMP TABLE legacy_trip_slots ON COMMIT DROP AS
WITH global_quota AS (
    SELECT COALESCE(MAX(CASE WHEN TRIM(value) ~ '^[0-9]+$' THEN TRIM(value)::INT END), 3) AS quota
    FROM config
    WHERE label = 'TRIP' AND key = 'FREE_TRIP_QUOTA' AND is_active = TRUE
),
free_left AS (
    SELECT u.id AS user_id,
           GREATEST(COALESCE(o.free_trip_quota, g.quota) - COALESCE(w.free_trip_quota_used, 0), 0) AS slots
    FROM users u
    CROSS JOIN global_quota g
    LEFT JOIN user_quota_overrides o ON o.user_id = u.id
    LEFT JOIN user_star_wallets w ON w.user_id = u.id
    WHERE u.deleted_at IS NULL
),
bought AS (
    SELECT user_id, COUNT(*)::INT AS slots
    FROM trip_creation_entitlements
    WHERE consumed_at IS NULL AND expires_at > NOW()
    GROUP BY user_id
)
SELECT f.user_id, f.slots AS free_slots, COALESCE(b.slots, 0) AS bought_slots
FROM free_left f
LEFT JOIN bought b ON b.user_id = f.user_id
WHERE f.slots + COALESCE(b.slots, 0) > 0;

INSERT INTO ai_credit_wallets (user_id)
SELECT user_id FROM legacy_trip_slots
ON CONFLICT (user_id) DO NOTHING;

UPDATE ai_credit_wallets w
SET earned_balance = w.earned_balance + (s.free_slots + s.bought_slots) * 200,
    updated_at = NOW()
FROM legacy_trip_slots s
WHERE w.user_id = s.user_id;

INSERT INTO ai_credit_transactions (
    id, user_id, transaction_type, amount, plan_amount, purchased_amount, earned_amount,
    plan_balance_after, purchased_balance_after, earned_balance_after, reference_key, feature, note)
SELECT gen_random_uuid(), w.user_id, 'MIGRATION_GRANT',
       (s.free_slots + s.bought_slots) * 200, 0, 0, (s.free_slots + s.bought_slots) * 200,
       w.plan_balance, w.purchased_balance, w.earned_balance,
       'trip_quota_migration:' || w.user_id, 'TRIP',
       s.free_slots || ' free trip(s) + ' || s.bought_slots || ' unlocked slot(s), 200 credits each'
FROM ai_credit_wallets w
JOIN legacy_trip_slots s ON s.user_id = w.user_id
ON CONFLICT (reference_key) DO NOTHING;

UPDATE trip_creation_entitlements e
SET converted_at = NOW()
FROM legacy_trip_slots s
WHERE e.user_id = s.user_id AND e.consumed_at IS NULL AND e.expires_at > NOW();

-- LEGACY-STAR-QUOTA: nothing reads these since V262; kept for rollback.
COMMENT ON COLUMN user_star_wallets.free_trip_quota_used IS 'Unused since V262 (trip creation costs credits).';
COMMENT ON COLUMN user_quota_overrides.free_trip_quota IS 'Unused since V262 (trip creation costs credits).';
