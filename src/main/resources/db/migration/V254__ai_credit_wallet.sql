-- AI credits replace the per-feature count limits (Pengoo questions per day, AI trips for life,
-- receipt scans per day, social video jobs per day). Every model call a user triggers is charged
-- its real cost x AI_CREDIT.MARKUP, in credits of AI_CREDIT.USD_PER_CREDIT each.
--
-- The wallet has two buckets. plan_balance is refilled by the user's plan every period (FREE
-- monthly, each PRO plan on its own cycle) and does not carry over; purchased_balance holds bought
-- credits and never expires. A charge takes the plan bucket first. Debt (a task that finished
-- below zero) sits in the plan bucket and is paid back by the next refill or purchase.
CREATE TABLE IF NOT EXISTS ai_credit_wallets (
    user_id           UUID PRIMARY KEY,
    plan_balance      NUMERIC(18, 4) NOT NULL DEFAULT 0,
    purchased_balance NUMERIC(18, 4) NOT NULL DEFAULT 0,
    plan_code         VARCHAR(40),
    plan_period_start TIMESTAMP,
    plan_period_end   TIMESTAMP,
    lifetime_spent    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_credit_wallet_purchased CHECK (purchased_balance >= 0)
);

-- Append-only. Every row says how much moved in each bucket and what both buckets held after it,
-- so a balance can be rebuilt from the ledger. reference_key makes every write idempotent.
CREATE TABLE IF NOT EXISTS ai_credit_transactions (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL,
    transaction_type        VARCHAR(20) NOT NULL,
    amount                  NUMERIC(18, 4) NOT NULL,
    plan_amount             NUMERIC(18, 4) NOT NULL,
    purchased_amount        NUMERIC(18, 4) NOT NULL,
    plan_balance_after      NUMERIC(18, 4) NOT NULL,
    purchased_balance_after NUMERIC(18, 4) NOT NULL,
    reference_key           VARCHAR(200) NOT NULL,
    feature                 VARCHAR(64),
    trace_id                VARCHAR(128),
    ai_api_call_id          UUID,
    cost_usd                NUMERIC(14, 6),
    reverses_transaction_id UUID,
    note                    VARCHAR(500),
    created_by              UUID,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ai_credit_transactions_reference UNIQUE (reference_key),
    CONSTRAINT chk_ai_credit_transaction_type
        CHECK (transaction_type IN ('PLAN_REFILL', 'CHARGE', 'REFUND', 'PURCHASE', 'ADJUST'))
);

CREATE INDEX IF NOT EXISTS idx_ai_credit_transactions_user_created
    ON ai_credit_transactions (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_credit_transactions_trace
    ON ai_credit_transactions (trace_id) WHERE trace_id IS NOT NULL;

-- Each PRO plan sets its own allowance and cycle: 1 = monthly, 3 = quarterly, 12 = yearly.
-- A yearly plan may still refill monthly. FREE is AI_CREDIT.FREE_MONTHLY_CREDITS, monthly.
ALTER TABLE subscription_plans
    ADD COLUMN IF NOT EXISTS ai_credits_per_refill NUMERIC(18, 4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_credit_refill_months INTEGER NOT NULL DEFAULT 1;

ALTER TABLE subscription_plans
    ADD CONSTRAINT chk_subscription_plan_ai_credits CHECK (
        ai_credits_per_refill >= 0 AND ai_credit_refill_months BETWEEN 1 AND 12
    );

UPDATE subscription_plans SET ai_credits_per_refill = 5000, ai_credit_refill_months = 1
WHERE code IN ('PRO_MONTHLY', 'PRO_YEARLY');

-- The count limits are gone; their knobs would only mislead an operator.
DELETE FROM config
WHERE (label, key) IN (
    ('ASSISTANT', 'FREE_DAILY_QUESTIONS'),
    ('ASSISTANT', 'PRO_DAILY_QUESTIONS'),
    ('AI_TRIP_QUOTA', 'FREE_TRIP_QUOTA'),
    ('AI_TRIP_QUOTA', 'PRO_TRIP_QUOTA'),
    ('EXPENSE', 'RECEIPT_SCAN_FREE_DAILY'),
    ('EXPENSE', 'RECEIPT_SCAN_PRO_DAILY'),
    ('SOCIAL_LOCATION', 'DAILY_JOB_LIMIT_DEFAULT')
);

INSERT INTO config (label, key, value, description, is_active)
VALUES ('AI_CREDIT', 'MARKUP', '1.2',
        'He so nhan chi phi AI that de ra credit (1.2 = lai 20%), 1-10', TRUE),
       ('AI_CREDIT', 'USD_PER_CREDIT', '0.001',
        'Gia ban cua 1 credit, USD', TRUE),
       ('AI_CREDIT', 'FREE_MONTHLY_CREDITS', '1000',
        'Credit nap moi thang cho tai khoan FREE (0-1000000); goi PRO dat trong subscription_plans', TRUE),
       ('AI_CREDIT', 'NEGATIVE_FLOOR', '5',
        'Duoc am toi da bao nhieu credit trong mot viec AI; qua muc thi dung (0-1000)', TRUE),
       ('AI_CREDIT', 'MIN_BALANCE_ASSISTANT', '20',
        'So credit toi thieu de hoi Pengoo (0-100000)', TRUE),
       ('AI_CREDIT', 'MIN_BALANCE_AI_TRIP', '100',
        'So credit toi thieu de tao lich trinh AI (0-100000)', TRUE),
       ('AI_CREDIT', 'MIN_BALANCE_RECEIPT_SCAN', '10',
        'So credit toi thieu de quet hoa don (0-100000)', TRUE),
       ('AI_CREDIT', 'MIN_BALANCE_SOCIAL_LOCATION', '10',
        'So credit toi thieu de trich dia diem tu video (0-100000)', TRUE),
       ('AI_CREDIT', 'FALLBACK_CREDITS_PER_CALL', '20',
        'Credit tru cho mot luot goi model chua co gia trong AI_PRICING (0-100000)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- Existing PRO rows keep working: the AI counter column stays for rollback, nothing reads it.
COMMENT ON COLUMN user_subscriptions.ai_trips_used IS 'Unused since V254 (AI credits).';
