-- Paid plans with an end date.
--
-- `user_subscriptions.expires_at` has existed since V041 and nothing has ever read it: the tier
-- lookup selected the `tier` column alone, so an account that was ever set to PRO stayed PRO for
-- good. This gives the column a catalogue to mean something against, and a ledger saying who
-- granted what.
--
-- NULL `expires_at` keeps meaning "no end date". Every PRO row written before today has NULL, and
-- reading NULL as expired would silently downgrade real accounts on the deploy that ships this.
-- Comped and lifetime access keep using NULL deliberately.

CREATE TABLE IF NOT EXISTS subscription_plans (
    code            VARCHAR(40) PRIMARY KEY,
    tier            VARCHAR(20)  NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    duration_days   INTEGER      NOT NULL,
    price_amount    NUMERIC(12,2) NOT NULL,
    price_currency  CHAR(3)      NOT NULL DEFAULT 'VND',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_plan_tier CHECK (tier IN ('FREE', 'PRO')),
    CONSTRAINT chk_subscription_plan_duration CHECK (duration_days BETWEEN 1 AND 3650),
    CONSTRAINT chk_subscription_plan_price CHECK (price_amount >= 0)
);

-- Priced in whole dong; the yearly plan is ten months' worth, which is the usual reason to take it.
INSERT INTO subscription_plans (code, tier, display_name, duration_days, price_amount, price_currency, sort_order)
VALUES
    ('PRO_MONTHLY', 'PRO', 'Vietdetour Pro - 1 thang',  30,   99000.00, 'VND', 1),
    ('PRO_YEARLY',  'PRO', 'Vietdetour Pro - 1 nam',   365,  990000.00, 'VND', 2)
ON CONFLICT (code) DO NOTHING;

-- Which plan produced the current period, so a renewal reminder knows what to offer.
ALTER TABLE user_subscriptions
    ADD COLUMN IF NOT EXISTS plan_code VARCHAR(40);

-- Finding what lapses soon, for the reminder job and the admin list. Partial, because free
-- accounts have no expiry to watch.
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_expiring
    ON user_subscriptions (expires_at)
    WHERE tier <> 'FREE' AND expires_at IS NOT NULL;

-- Append-only record of every period ever granted.
--
-- The unique reference key is what makes granting idempotent: a retried payment webhook, or a
-- double-tapped admin button, builds the same key and the second attempt loses to the index
-- instead of quietly adding another year.
CREATE TABLE IF NOT EXISTS user_subscription_grants (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL,
    plan_code      VARCHAR(40) NOT NULL,
    tier           VARCHAR(20) NOT NULL,
    duration_days  INTEGER     NOT NULL,
    starts_at      TIMESTAMP   NOT NULL,
    expires_at     TIMESTAMP   NOT NULL,
    reference_key  VARCHAR(200) NOT NULL,
    granted_by     UUID,
    note           TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_subscription_grant_reference UNIQUE (reference_key)
);

CREATE INDEX IF NOT EXISTS idx_user_subscription_grants_user
    ON user_subscription_grants (user_id, created_at DESC);

-- Console access. `get` is reading somebody's plan and its history; `update` is granting or
-- revoking one. Kept apart so support can answer "when does my Pro end" without also being able
-- to hand out plans.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('subscriptions', 'get'),
    ('subscriptions', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'subscriptions'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
