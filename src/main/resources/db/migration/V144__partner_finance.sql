-- Partner finance: frozen commission on bookings, monthly statements, per-line disputes.
-- Money is only recorded and reconciled here; there is still no payment gateway and no payout
-- execution, so nothing in this migration charges or moves money.

-- Commission rate owned by the organization ------------------------------------------------------
ALTER TABLE host_organizations
    ADD COLUMN IF NOT EXISTS commission_percent NUMERIC(5,2) NOT NULL DEFAULT 15.00,
    ADD COLUMN IF NOT EXISTS billing_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS billing_details JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE host_organizations
    DROP CONSTRAINT IF EXISTS chk_host_org_commission_percent;
ALTER TABLE host_organizations
    ADD CONSTRAINT chk_host_org_commission_percent CHECK (commission_percent >= 0 AND commission_percent <= 50);

-- Commission frozen on the booking at creation time. Changing the organization percentage must
-- never re-price a booking that was already taken, so the rate and the rule version travel with
-- the row instead of being looked up at statement time.
ALTER TABLE hotel_bookings
    ADD COLUMN IF NOT EXISTS commission_percent NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS commission_rule_version VARCHAR(32);
ALTER TABLE activity_orders
    ADD COLUMN IF NOT EXISTS commission_percent NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS commission_rule_version VARCHAR(32);

-- Rows the statement generator still has to stamp (partial index: the stamped majority stays out).
CREATE INDEX IF NOT EXISTS idx_hotel_bookings_unstamped_commission
    ON hotel_bookings(organization_id, check_out_date) WHERE commission_percent IS NULL;
CREATE INDEX IF NOT EXISTS idx_activity_orders_unstamped_commission
    ON activity_orders(organization_id) WHERE commission_percent IS NULL;

-- Monthly statement per organization -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS partner_statements (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    gross_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    commission_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    booking_count INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    issued_at TIMESTAMP,
    settled_at TIMESTAMP,
    note TEXT,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_partner_statement_period UNIQUE (organization_id, period_start, period_end),
    CONSTRAINT chk_partner_statement_period CHECK (period_end >= period_start),
    CONSTRAINT chk_partner_statement_status CHECK (status IN ('OPEN','ISSUED','DISPUTED','SETTLED'))
);
CREATE INDEX IF NOT EXISTS idx_partner_statements_org_period
    ON partner_statements(organization_id, period_start DESC);

CREATE TABLE IF NOT EXISTS partner_statement_lines (
    id UUID PRIMARY KEY,
    statement_id UUID NOT NULL REFERENCES partner_statements(id) ON DELETE CASCADE,
    booking_type VARCHAR(16) NOT NULL,
    hotel_booking_id UUID REFERENCES hotel_bookings(id) ON DELETE SET NULL,
    activity_order_id UUID REFERENCES activity_orders(id) ON DELETE SET NULL,
    booking_code VARCHAR(64),
    guest_name VARCHAR(255),
    service_date DATE,
    gross_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    commission_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    commission_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_reason VARCHAR(32) NOT NULL,
    dispute_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    dispute_reason TEXT,
    dispute_opened_at TIMESTAMP,
    dispute_resolved_at TIMESTAMP,
    dispute_resolved_by UUID,
    dispute_resolution_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_partner_statement_line_type CHECK (booking_type IN ('HOTEL','ACTIVITY')),
    CONSTRAINT chk_partner_statement_line_reason CHECK (line_reason IN ('COMPLETED','NO_SHOW_CHARGED','CANCELLATION_FEE')),
    CONSTRAINT chk_partner_statement_line_dispute CHECK (dispute_status IN ('NONE','OPEN','ACCEPTED','REJECTED')),
    CONSTRAINT chk_partner_statement_line_target CHECK (
        (booking_type = 'HOTEL' AND hotel_booking_id IS NOT NULL AND activity_order_id IS NULL) OR
        (booking_type = 'ACTIVITY' AND activity_order_id IS NOT NULL AND hotel_booking_id IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_partner_statement_lines_statement
    ON partner_statement_lines(statement_id, service_date, created_at);
-- One line per booking per statement: regenerating a statement must not duplicate a stay.
CREATE UNIQUE INDEX IF NOT EXISTS uq_partner_statement_line_hotel
    ON partner_statement_lines(statement_id, hotel_booking_id) WHERE hotel_booking_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_partner_statement_line_activity
    ON partner_statement_lines(statement_id, activity_order_id) WHERE activity_order_id IS NOT NULL;

-- Runtime policy knobs owned by BusinessConfigKey (MARKETPLACE label).
INSERT INTO config (label, key, value, description, is_active) VALUES
    ('MARKETPLACE', 'COMMISSION_RULE_VERSION', '2026.09', 'Phien ban quy tac hoa hong duoc dong bang len moi booking/order moi', TRUE),
    ('MARKETPLACE', 'STATEMENT_DISPUTE_WINDOW_DAYS', '14', 'So ngay ke tu khi phat hanh sao ke partner con duoc mo tranh chap (1..90)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
