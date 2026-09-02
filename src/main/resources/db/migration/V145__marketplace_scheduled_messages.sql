-- Scheduled guest messages: a partner writes a rule once, the job turns it into a normal chat
-- message in the booking conversation at the right moment. No new notification type: the guest
-- sees it exactly like a message a human typed.

CREATE TABLE IF NOT EXISTS marketplace_scheduled_messages (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    -- Optional link to the quick reply the body was copied from; the body is still stored inline so
    -- editing or deleting the quick reply never silently changes what guests receive.
    template_id UUID REFERENCES marketplace_message_templates(id) ON DELETE SET NULL,
    trigger_type VARCHAR(32) NOT NULL,
    offset_hours INTEGER NOT NULL DEFAULT 24,
    body TEXT NOT NULL,
    applies_to VARCHAR(16) NOT NULL DEFAULT 'ALL',
    status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_scheduled_message_trigger CHECK (trigger_type IN ('ON_BOOKING_CONFIRMED','BEFORE_CHECK_IN','AFTER_CHECK_OUT','BEFORE_ACTIVITY')),
    CONSTRAINT chk_scheduled_message_applies_to CHECK (applies_to IN ('HOTEL','ACTIVITY','ALL')),
    CONSTRAINT chk_scheduled_message_status CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT chk_scheduled_message_offset CHECK (offset_hours BETWEEN 0 AND 8760),
    CONSTRAINT chk_scheduled_message_body CHECK (char_length(body) BETWEEN 1 AND 2000)
);

CREATE INDEX IF NOT EXISTS idx_scheduled_messages_org ON marketplace_scheduled_messages(organization_id, created_at);
-- The job scans every enabled rule on each tick; keep that scan off the organization index.
CREATE INDEX IF NOT EXISTS idx_scheduled_messages_enabled ON marketplace_scheduled_messages(id)
    WHERE status = 'ENABLED';

CREATE TABLE IF NOT EXISTS marketplace_scheduled_message_runs (
    id UUID PRIMARY KEY,
    scheduled_message_id UUID NOT NULL REFERENCES marketplace_scheduled_messages(id) ON DELETE CASCADE,
    booking_type VARCHAR(16) NOT NULL,
    hotel_booking_id UUID REFERENCES hotel_bookings(id) ON DELETE CASCADE,
    activity_order_id UUID REFERENCES activity_orders(id) ON DELETE CASCADE,
    conversation_id UUID REFERENCES marketplace_conversations(id) ON DELETE SET NULL,
    message_id UUID REFERENCES marketplace_messages(id) ON DELETE SET NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR(24) NOT NULL,
    detail TEXT,
    CONSTRAINT chk_scheduled_run_booking_type CHECK (booking_type IN ('HOTEL','ACTIVITY')),
    CONSTRAINT chk_scheduled_run_status CHECK (status IN ('SENT','SKIPPED','FAILED')),
    CONSTRAINT chk_scheduled_run_target CHECK (num_nonnulls(hotel_booking_id, activity_order_id) = 1)
);

-- The duplicate guard. A plain UNIQUE (scheduled_message_id, hotel_booking_id, activity_order_id)
-- would NOT work here: PostgreSQL treats NULLs as distinct, so every activity run (hotel id NULL)
-- and every hotel run (order id NULL) would be allowed through unlimited times. Two partial unique
-- indexes give the guarantee the job relies on — two overlapping job runs racing on the same
-- (rule, booking) pair, the loser's transaction rolls back and its message is never delivered.
CREATE UNIQUE INDEX IF NOT EXISTS uq_scheduled_run_hotel
    ON marketplace_scheduled_message_runs(scheduled_message_id, hotel_booking_id)
    WHERE hotel_booking_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_scheduled_run_activity
    ON marketplace_scheduled_message_runs(scheduled_message_id, activity_order_id)
    WHERE activity_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_scheduled_runs_recent
    ON marketplace_scheduled_message_runs(scheduled_message_id, sent_at DESC);
