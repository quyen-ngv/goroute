-- Marketplace catalog completeness and OTA-style rate calendar.
-- Room inventory remains shared by all rate plans. Price and rate restrictions
-- live at rate-plan/date level and override the rate plan defaults.

ALTER TABLE hotel_profiles
    ADD COLUMN IF NOT EXISTS languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS reception_hours JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS house_rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS accessibility_features JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS parking_details JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE room_types
    ADD COLUMN IF NOT EXISTS standard_adults INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS max_infants INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bedroom_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS bathroom_count DECIMAL(4,1) NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS view_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bathroom_type VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS smoking_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS accessibility_features JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE room_types
    ADD CONSTRAINT chk_room_type_extended_capacity
        CHECK (standard_adults >= 1 AND standard_adults <= max_adults AND max_infants >= 0),
    ADD CONSTRAINT chk_room_type_spaces
        CHECK (bedroom_count >= 0 AND bathroom_count >= 0),
    ADD CONSTRAINT chk_room_type_bathroom
        CHECK (bathroom_type IN ('PRIVATE','SHARED','ENSUITE'));

ALTER TABLE rate_plans
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS pricing_model VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS base_occupancy INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS extra_adult_fee DECIMAL(18,2),
    ADD COLUMN IF NOT EXISTS extra_child_fee DECIMAL(18,2),
    ADD COLUMN IF NOT EXISTS included_benefits JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS prepayment_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS no_show_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS min_advance_days INTEGER,
    ADD COLUMN IF NOT EXISTS max_advance_days INTEGER,
    ADD COLUMN IF NOT EXISTS refundable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE rate_plans
    ADD CONSTRAINT chk_rate_plan_pricing_model
        CHECK (pricing_model IN ('STANDARD','OCCUPANCY_BASED','LENGTH_OF_STAY','DERIVED')),
    ADD CONSTRAINT chk_rate_plan_occupancy CHECK (base_occupancy >= 1),
    ADD CONSTRAINT chk_rate_plan_extra_fees CHECK (
        (extra_adult_fee IS NULL OR extra_adult_fee >= 0)
        AND (extra_child_fee IS NULL OR extra_child_fee >= 0)
    ),
    ADD CONSTRAINT chk_rate_plan_advance_window CHECK (
        (min_advance_days IS NULL OR min_advance_days >= 0)
        AND (max_advance_days IS NULL OR max_advance_days >= COALESCE(min_advance_days, 0))
    );

CREATE TABLE rate_plan_daily_rates (
    rate_plan_id UUID NOT NULL REFERENCES rate_plans(id) ON DELETE CASCADE,
    rate_date DATE NOT NULL,
    price DECIMAL(18,2),
    stop_sell BOOLEAN NOT NULL DEFAULT FALSE,
    min_stay INTEGER,
    max_stay INTEGER,
    closed_to_arrival BOOLEAN NOT NULL DEFAULT FALSE,
    closed_to_departure BOOLEAN NOT NULL DEFAULT FALSE,
    min_advance_days INTEGER,
    max_advance_days INTEGER,
    data_version BIGINT NOT NULL DEFAULT 1,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (rate_plan_id, rate_date),
    CONSTRAINT chk_daily_rate_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT chk_daily_rate_stay CHECK (
        (min_stay IS NULL OR min_stay >= 1)
        AND (max_stay IS NULL OR max_stay >= COALESCE(min_stay, 1))
    ),
    CONSTRAINT chk_daily_rate_advance CHECK (
        (min_advance_days IS NULL OR min_advance_days >= 0)
        AND (max_advance_days IS NULL OR max_advance_days >= COALESCE(min_advance_days, 0))
    )
);
CREATE INDEX idx_rate_plan_daily_rates_date ON rate_plan_daily_rates(rate_date, rate_plan_id);

ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS activity_type VARCHAR(32) NOT NULL DEFAULT 'TOUR',
    ADD COLUMN IF NOT EXISTS languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS meeting_point TEXT,
    ADD COLUMN IF NOT EXISTS included_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS excluded_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS eligibility JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS accessibility_features JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS confirmation_type VARCHAR(24) NOT NULL DEFAULT 'INSTANT',
    ADD COLUMN IF NOT EXISTS voucher_type VARCHAR(24) NOT NULL DEFAULT 'QR_CODE',
    ADD COLUMN IF NOT EXISTS redemption_instructions TEXT,
    ADD COLUMN IF NOT EXISTS cancellation_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS required_information JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE activity_bookings
    ADD CONSTRAINT chk_activity_type CHECK (activity_type IN ('TOUR','ATTRACTION','TRANSFER','CLASS','RENTAL','EVENT','PASS','OTHER')),
    ADD CONSTRAINT chk_activity_confirmation_type CHECK (confirmation_type IN ('INSTANT','MANUAL')),
    ADD CONSTRAINT chk_activity_voucher_type CHECK (voucher_type IN ('QR_CODE','BARCODE','MOBILE','PAPERLESS','PRINTED'));

ALTER TABLE activity_packages
    ADD COLUMN IF NOT EXISTS inventory_type VARCHAR(24) NOT NULL DEFAULT 'SLOT',
    ADD COLUMN IF NOT EXISTS units JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS included_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS excluded_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS required_information JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS confirmation_type VARCHAR(24) NOT NULL DEFAULT 'INSTANT',
    ADD COLUMN IF NOT EXISTS voucher_type VARCHAR(24) NOT NULL DEFAULT 'QR_CODE',
    ADD COLUMN IF NOT EXISTS validity_days INTEGER;

ALTER TABLE activity_packages
    ADD CONSTRAINT chk_activity_package_inventory_type CHECK (inventory_type IN ('UNLIMITED','DAILY','SLOT','RESOURCE')),
    ADD CONSTRAINT chk_activity_package_confirmation_type CHECK (confirmation_type IN ('INSTANT','MANUAL')),
    ADD CONSTRAINT chk_activity_package_voucher_type CHECK (voucher_type IN ('QR_CODE','BARCODE','MOBILE','PAPERLESS','PRINTED')),
    ADD CONSTRAINT chk_activity_package_validity CHECK (validity_days IS NULL OR validity_days >= 1);

ALTER TABLE activity_slots
    ADD COLUMN IF NOT EXISTS unit_prices JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS all_day BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS meeting_point_override TEXT;

ALTER TABLE hotel_bookings
    ADD COLUMN IF NOT EXISTS guest_details JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS special_requests TEXT,
    ADD COLUMN IF NOT EXISTS estimated_arrival_time TIME;

ALTER TABLE activity_orders
    ADD COLUMN IF NOT EXISTS contact_info JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS special_requests TEXT,
    ADD COLUMN IF NOT EXISTS redemption_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REDEEMED',
    ADD COLUMN IF NOT EXISTS redeemed_at TIMESTAMP;

ALTER TABLE activity_orders
    ADD CONSTRAINT chk_activity_order_redemption_status
        CHECK (redemption_status IN ('NOT_REDEEMED','PARTIALLY_REDEEMED','REDEEMED','VOIDED'));

ALTER TABLE activity_order_items
    ADD COLUMN IF NOT EXISTS unit_selections JSONB NOT NULL DEFAULT '{}'::jsonb;
