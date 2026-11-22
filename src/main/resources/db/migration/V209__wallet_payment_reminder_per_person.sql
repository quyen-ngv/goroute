-- Wallet payment reminders are sent per person: one tap reminds a recipient about every
-- open split the payer picked, as a single notification. The ledger keeps one row per
-- split, so a request's rows share one Idempotency-Key.
ALTER TABLE expense_payment_reminders
    DROP CONSTRAINT IF EXISTS expense_payment_reminders_sender_key_unique;
ALTER TABLE expense_payment_reminders
    ADD CONSTRAINT expense_payment_reminders_sender_key_split_unique
        UNIQUE (sender_user_id, idempotency_key, split_id);

-- The throttle moves from "each split once per cooldown" to "each person at most
-- EXPENSE.PAYMENT_REMINDER_DAILY_MAX reminders per day (Asia/Ho_Chi_Minh)".
-- One row per payer, recipient and day; sent_count is what the limit is checked against.
CREATE TABLE IF NOT EXISTS expense_payment_reminder_daily (
    sender_user_id    UUID      NOT NULL,
    recipient_user_id UUID      NOT NULL,
    reminder_date     DATE      NOT NULL,
    sent_count        INTEGER   NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sender_user_id, recipient_user_id, reminder_date),
    CONSTRAINT chk_expense_payment_reminder_daily_count CHECK (sent_count >= 0)
);

-- Held only last-sent timestamps for the per-split cooldown it replaces.
DROP TABLE IF EXISTS expense_payment_reminder_limits;

DELETE FROM config WHERE label = 'EXPENSE' AND key = 'PAYMENT_REMINDER_COOLDOWN_MINUTES';

INSERT INTO config (label, key, value, description, is_active)
VALUES ('EXPENSE', 'PAYMENT_REMINDER_DAILY_MAX', '2',
        'So lan toi da mot nguoi tra tien duoc nhac cung mot nguoi trong 1 ngay (1-20)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
