-- Partner marketplace: full operational flow (verification, readiness, vouchers, change requests,
-- quality snapshots, quick replies, iCal, booking-linked reviews). Payment integration is still pending.

-- Organization verification workflow ------------------------------------------------------------
ALTER TABLE host_organizations
    ADD COLUMN IF NOT EXISTS verification_submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS verification_reason TEXT,
    ADD COLUMN IF NOT EXISTS verification_decided_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS verification_decided_by UUID;

CREATE TABLE IF NOT EXISTS organization_verification_documents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    file_url TEXT NOT NULL,
    file_name VARCHAR(255),
    note TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    review_note TEXT,
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    uploaded_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_org_verification_doc_kind CHECK (kind IN ('BUSINESS_LICENSE','TAX_CERTIFICATE','OWNER_ID','BANK_ACCOUNT','PROPERTY_PROOF','OTHER')),
    CONSTRAINT chk_org_verification_doc_status CHECK (status IN ('SUBMITTED','ACCEPTED','REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_org_verification_docs_org ON organization_verification_documents(organization_id, created_at);
CREATE INDEX IF NOT EXISTS idx_host_org_verification_pending ON host_organizations(verification_submitted_at)
    WHERE verification_status = 'PENDING';

-- Partner quality snapshots (rolling window, recomputed by PartnerQualityJob) ----------------------
CREATE TABLE IF NOT EXISTS partner_quality_snapshots (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    window_days INT NOT NULL DEFAULT 90,
    computed_at TIMESTAMP NOT NULL,
    bookings_total INT NOT NULL DEFAULT 0,
    bookings_confirmed INT NOT NULL DEFAULT 0,
    bookings_cancelled_by_host INT NOT NULL DEFAULT 0,
    bookings_no_show INT NOT NULL DEFAULT 0,
    bookings_expired INT NOT NULL DEFAULT 0,
    avg_response_minutes INT,
    response_within_sla_rate NUMERIC(5,2),
    host_cancellation_rate NUMERIC(5,2),
    no_show_rate NUMERIC(5,2),
    expiry_rate NUMERIC(5,2),
    review_count INT NOT NULL DEFAULT 0,
    review_average NUMERIC(3,2),
    badge VARCHAR(24) NOT NULL DEFAULT 'NONE',
    CONSTRAINT uq_partner_quality_window UNIQUE (organization_id, window_days)
);

-- Quick replies for the partner inbox -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS marketplace_message_templates (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_marketplace_message_templates_org ON marketplace_message_templates(organization_id, sort_order);

-- Guest-initiated change requests (dates/guests for stays, slot for activities) -------------------
CREATE TABLE IF NOT EXISTS booking_change_requests (
    id UUID PRIMARY KEY,
    booking_type VARCHAR(16) NOT NULL,
    hotel_booking_id UUID REFERENCES hotel_bookings(id) ON DELETE CASCADE,
    activity_order_id UUID REFERENCES activity_orders(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    requested_by UUID,
    new_check_in_date DATE,
    new_check_out_date DATE,
    new_adults INT,
    new_children INT,
    new_slot_id UUID REFERENCES activity_slots(id) ON DELETE SET NULL,
    message TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    responded_by UUID,
    response_note TEXT,
    responded_at TIMESTAMP,
    price_before NUMERIC(18,2),
    price_after NUMERIC(18,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_booking_change_type CHECK (booking_type IN ('HOTEL','ACTIVITY')),
    CONSTRAINT chk_booking_change_status CHECK (status IN ('REQUESTED','ACCEPTED','DECLINED','WITHDRAWN','EXPIRED')),
    CONSTRAINT chk_booking_change_target CHECK (
        (booking_type = 'HOTEL' AND hotel_booking_id IS NOT NULL AND activity_order_id IS NULL) OR
        (booking_type = 'ACTIVITY' AND activity_order_id IS NOT NULL AND hotel_booking_id IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_booking_change_requests_org_status ON booking_change_requests(organization_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_booking_change_requests_hotel_booking ON booking_change_requests(hotel_booking_id);
CREATE INDEX IF NOT EXISTS idx_booking_change_requests_activity_order ON booking_change_requests(activity_order_id);
-- At most one open request per booking.
CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_change_open_hotel ON booking_change_requests(hotel_booking_id)
    WHERE status = 'REQUESTED' AND hotel_booking_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_change_open_activity ON booking_change_requests(activity_order_id)
    WHERE status = 'REQUESTED' AND activity_order_id IS NOT NULL;

-- No-show accounting and voucher redemption ------------------------------------------------------
ALTER TABLE hotel_bookings ADD COLUMN IF NOT EXISTS guest_charged BOOLEAN;
ALTER TABLE activity_orders
    ADD COLUMN IF NOT EXISTS guest_charged BOOLEAN,
    ADD COLUMN IF NOT EXISTS redeemed_by UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_activity_orders_voucher_code ON activity_orders(voucher_code) WHERE voucher_code IS NOT NULL;

-- iCal export per room type ----------------------------------------------------------------------
ALTER TABLE room_types ADD COLUMN IF NOT EXISTS ical_token VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_room_types_ical_token ON room_types(ical_token) WHERE ical_token IS NOT NULL;

-- Reviews tied to a completed stay / visit -------------------------------------------------------
ALTER TABLE user_reviews
    ADD COLUMN IF NOT EXISTS hotel_booking_id UUID REFERENCES hotel_bookings(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS activity_order_id UUID REFERENCES activity_orders(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_reviews_hotel_booking ON user_reviews(hotel_booking_id) WHERE hotel_booking_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_reviews_activity_order ON user_reviews(activity_order_id) WHERE activity_order_id IS NOT NULL;
