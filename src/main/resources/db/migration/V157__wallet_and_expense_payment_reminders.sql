-- Global Wallet reads existing expenses; these tables make payment reminders idempotent and rate limited.
CREATE TABLE IF NOT EXISTS expense_payment_reminder_limits (
    sender_user_id UUID NOT NULL,
    split_id UUID NOT NULL,
    last_sent_at TIMESTAMP NOT NULL,
    PRIMARY KEY (sender_user_id, split_id)
);

CREATE TABLE IF NOT EXISTS expense_payment_reminders (
    id UUID PRIMARY KEY,
    sender_user_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    expense_id UUID NOT NULL,
    split_id UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT expense_payment_reminders_sender_key_unique UNIQUE (sender_user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_expense_payment_reminders_expense_split
    ON expense_payment_reminders (expense_id, split_id, created_at DESC);
