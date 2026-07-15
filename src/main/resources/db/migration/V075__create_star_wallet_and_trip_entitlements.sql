CREATE TABLE IF NOT EXISTS user_star_wallets (
    user_id UUID PRIMARY KEY,
    balance INTEGER NOT NULL DEFAULT 0,
    free_trip_quota_used INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS star_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    amount INTEGER NOT NULL,
    transaction_type VARCHAR(64) NOT NULL,
    reference_key VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_star_transaction_reference UNIQUE (reference_key)
);

CREATE INDEX IF NOT EXISTS idx_star_transactions_user_created
    ON star_transactions (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS trip_creation_entitlements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    stars_spent INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trip_entitlements_user_active
    ON trip_creation_entitlements (user_id, expires_at, consumed_at);
