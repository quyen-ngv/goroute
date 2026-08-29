-- Epic 07, VietdeGuide. A separate product from the hotel/activity marketplace: it shares
-- infrastructure (chat, notifications, reviews, admin queues) but not its catalogue model.
--
-- Scope note: GUIDE-07 (paid placement) is not created here. The epic itself recommends
-- deferring it during the pilot, and an unlabelled or inconsistently labelled ad slot is
-- the one thing in this epic that damages trust irreversibly. The single column that makes
-- labelling possible later (`is_promoted`) is present so the label can never be a display
-- decision.

CREATE TABLE guide_profiles (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    display_name       VARCHAR(200) NOT NULL,
    headline           VARCHAR(300),
    bio                TEXT,
    languages          JSONB NOT NULL DEFAULT '[]'::jsonb,
    area_province_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    years_experience   SMALLINT,
    avatar_url         VARCHAR(1000),
    -- Contact details are internal. The public profile must carry enough to decide to
    -- hire, not everything the applicant supplied.
    contact_phone      VARCHAR(40),
    contact_email      VARCHAR(200),

    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    submitted_at       TIMESTAMP,
    decided_at         TIMESTAMP,
    decided_by         UUID,
    decision_note      TEXT,
    -- Set when an operator asks for more paperwork, so the applicant sees what is missing.
    information_requested TEXT,

    plan               VARCHAR(20) NOT NULL DEFAULT 'FREE',
    plan_expires_at    TIMESTAMP,

    response_minutes_avg INT,
    completed_bookings INT NOT NULL DEFAULT 0,
    rating_average     NUMERIC(3, 2),
    rating_count       INT NOT NULL DEFAULT 0,

    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_guide_status
        CHECK (status IN ('DRAFT', 'PENDING_VERIFICATION', 'APPROVED', 'SUSPENDED', 'REJECTED')),
    CONSTRAINT ck_guide_plan CHECK (plan IN ('FREE', 'PREMIUM'))
);
CREATE INDEX idx_guide_profiles_queue ON guide_profiles (status, submitted_at);
CREATE INDEX idx_guide_profiles_public ON guide_profiles (status) WHERE status = 'APPROVED';

-- Identity documents are held apart from the profile with a shorter life and a narrower
-- audience. Storing them at all is a compliance obligation, not a convenience.
CREATE TABLE guide_identity_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guide_id     UUID NOT NULL REFERENCES guide_profiles (id) ON DELETE CASCADE,
    document_type VARCHAR(40) NOT NULL,
    file_url     VARCHAR(1000) NOT NULL,
    uploaded_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    -- Automatic disposal date. A document with no deletion date becomes a permanent
    -- liability.
    purge_after  TIMESTAMP NOT NULL,
    purged_at    TIMESTAMP
);
CREATE INDEX idx_guide_documents_purge ON guide_identity_documents (purge_after)
    WHERE purged_at IS NULL;

CREATE TABLE guide_services (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guide_id        UUID NOT NULL REFERENCES guide_profiles (id) ON DELETE CASCADE,
    title           VARCHAR(300) NOT NULL,
    summary         TEXT,
    itinerary       TEXT,
    duration_hours  NUMERIC(5, 2) NOT NULL,
    max_guests      SMALLINT NOT NULL CHECK (max_guests > 0),
    meeting_point   VARCHAR(500),
    province_code   VARCHAR(10) REFERENCES provinces (code) ON DELETE SET NULL,

    -- Structured rather than free text: this is where most disputes start, and a
    -- structured list can be compared between services.
    inclusions      JSONB NOT NULL DEFAULT '[]'::jsonb,
    exclusions      JSONB NOT NULL DEFAULT '[]'::jsonb,

    pricing_mode    VARCHAR(20) NOT NULL,
    price_amount    NUMERIC(14, 2) NOT NULL CHECK (price_amount >= 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'VND',
    advance_notice_hours INT NOT NULL DEFAULT 24,

    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    is_promoted     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_guide_service_status CHECK (status IN ('DRAFT', 'LISTED', 'PAUSED')),
    CONSTRAINT ck_guide_pricing_mode CHECK (pricing_mode IN ('PER_PERSON', 'PER_GROUP'))
);
CREATE INDEX idx_guide_services_guide ON guide_services (guide_id, status);
CREATE INDEX idx_guide_services_search ON guide_services (province_code, status)
    WHERE status = 'LISTED';

-- A guide is one person, so capacity is guests per slot, not rooms.
CREATE TABLE guide_availability (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guide_id      UUID NOT NULL REFERENCES guide_profiles (id) ON DELETE CASCADE,
    available_date DATE NOT NULL,
    is_blocked    BOOLEAN NOT NULL DEFAULT FALSE,
    max_guests    SMALLINT,
    note          VARCHAR(300),
    CONSTRAINT uq_guide_availability UNIQUE (guide_id, available_date)
);
CREATE INDEX idx_guide_availability_date ON guide_availability (guide_id, available_date);

CREATE TABLE guide_bookings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id       UUID NOT NULL REFERENCES guide_services (id) ON DELETE RESTRICT,
    guide_id         UUID NOT NULL REFERENCES guide_profiles (id) ON DELETE RESTRICT,
    traveler_id      UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    booking_date     DATE NOT NULL,
    guest_count      SMALLINT NOT NULL CHECK (guest_count > 0),
    traveler_note    TEXT,

    -- Money is copied onto the booking at confirmation and never read from the current
    -- price or the current fee rule again. A later price change is for later bookings.
    currency         VARCHAR(3) NOT NULL DEFAULT 'VND',
    service_amount   NUMERIC(14, 2) NOT NULL,
    platform_fee_percent NUMERIC(5, 2) NOT NULL,
    platform_fee_amount  NUMERIC(14, 2) NOT NULL,
    guide_payout_amount  NUMERIC(14, 2) NOT NULL,
    fee_rule_version VARCHAR(20) NOT NULL,

    status           VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    payment_status   VARCHAR(20) NOT NULL DEFAULT 'NONE',
    respond_by       TIMESTAMP,
    confirmed_at     TIMESTAMP,
    completed_at     TIMESTAMP,
    cancelled_at     TIMESTAMP,
    cancellation_reason TEXT,
    dispute_opened_at TIMESTAMP,
    payout_released_at TIMESTAMP,
    payout_frozen    BOOLEAN NOT NULL DEFAULT FALSE,

    idempotency_key  VARCHAR(120) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_guide_booking_idempotency UNIQUE (traveler_id, idempotency_key),
    CONSTRAINT ck_guide_booking_status CHECK (status IN
        ('REQUESTED', 'ACCEPTED', 'DECLINED', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'DISPUTED', 'EXPIRED')),
    CONSTRAINT ck_guide_payment_status CHECK (payment_status IN
        ('NONE', 'HELD', 'CAPTURED', 'REFUNDED', 'RELEASED'))
);
CREATE INDEX idx_guide_bookings_guide ON guide_bookings (guide_id, status, booking_date);
CREATE INDEX idx_guide_bookings_traveler ON guide_bookings (traveler_id, created_at DESC);
-- Capacity is enforced by counting live bookings for a date; this index is what makes the
-- check cheap enough to run inside the booking transaction.
CREATE INDEX idx_guide_bookings_capacity ON guide_bookings (guide_id, booking_date)
    WHERE status IN ('ACCEPTED', 'CONFIRMED', 'COMPLETED');
CREATE INDEX idx_guide_bookings_payout ON guide_bookings (payout_released_at, completed_at)
    WHERE status = 'COMPLETED' AND payout_released_at IS NULL;

-- A separate ledger for real money. It must never be mixed with the point wallet.
CREATE TABLE guide_payout_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID NOT NULL REFERENCES guide_bookings (id) ON DELETE RESTRICT,
    guide_id     UUID NOT NULL REFERENCES guide_profiles (id) ON DELETE RESTRICT,
    entry_type   VARCHAR(20) NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL,
    currency     VARCHAR(3) NOT NULL DEFAULT 'VND',
    -- Adjustments are new entries. Financial history is never edited.
    reverses_entry_id UUID REFERENCES guide_payout_entries (id) ON DELETE SET NULL,
    reference_key VARCHAR(160) NOT NULL UNIQUE,
    note         TEXT,
    created_by   UUID,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payout_entry_type CHECK (entry_type IN
        ('HOLD', 'CAPTURE', 'PLATFORM_FEE', 'PAYOUT', 'REFUND', 'ADJUSTMENT'))
);
CREATE INDEX idx_payout_entries_guide ON guide_payout_entries (guide_id, created_at DESC);

-- GUIDE-08. Only a completed booking can be reviewed; that constraint is the whole value
-- of the rating.
CREATE TABLE guide_reviews (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID NOT NULL UNIQUE REFERENCES guide_bookings (id) ON DELETE CASCADE,
    guide_id     UUID NOT NULL REFERENCES guide_profiles (id) ON DELETE CASCADE,
    traveler_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating       SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      TEXT,
    guide_response TEXT,
    responded_at TIMESTAMP,
    is_removed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_guide_reviews_guide ON guide_reviews (guide_id, created_at DESC)
    WHERE is_removed = FALSE;

-- Counters GUIDE-02 must compute from real data rather than accept from the guide.
CREATE TABLE guide_service_views (
    service_id   UUID NOT NULL REFERENCES guide_services (id) ON DELETE CASCADE,
    view_date    DATE NOT NULL,
    view_count   INT NOT NULL DEFAULT 0,
    inquiry_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (service_id, view_date)
);
