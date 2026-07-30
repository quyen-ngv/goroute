-- Partner marketplace foundation: canonical place sources, organizations, hotels,
-- room inventory, bookings, activity commerce, chat, reviews and immutable history.

-- -----------------------------------------------------------------------------
-- Canonical place extensions (additive/compatible with the Google import flow)
-- -----------------------------------------------------------------------------
ALTER TABLE places ALTER COLUMN place_id DROP NOT NULL;
ALTER TABLE places
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS primary_source_type VARCHAR(24) NOT NULL DEFAULT 'GOOGLE_MAPS',
    ADD COLUMN IF NOT EXISTS data_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS merged_into_place_id UUID,
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID;

CREATE TABLE IF NOT EXISTS place_sources (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    source_type VARCHAR(24) NOT NULL,
    source_owner_id UUID,
    external_id VARCHAR(500),
    external_url TEXT,
    cid VARCHAR(255),
    data_id VARCHAR(255),
    source_locale VARCHAR(16),
    source_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    first_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_successful_sync_at TIMESTAMP,
    payload_checksum VARCHAR(128),
    schema_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_place_sources_external
    ON place_sources(source_type, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_place_sources_place ON place_sources(place_id);

INSERT INTO place_sources (
    id, place_id, source_type, external_id, external_url, cid, data_id,
    source_locale, source_status, first_seen_at, last_seen_at,
    last_successful_sync_at, schema_version, created_at, updated_at
)
SELECT gen_random_uuid(), p.id, 'GOOGLE_MAPS', p.place_id, p.google_maps_link, p.cid, p.data_id,
       'en', 'ACTIVE', COALESCE(p.created_at, NOW()), COALESCE(p.updated_at, NOW()),
       p.updated_at, 1, COALESCE(p.created_at, NOW()), COALESCE(p.updated_at, NOW())
FROM places p
WHERE p.place_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS place_source_snapshots (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES place_sources(id) ON DELETE CASCADE,
    observed_at TIMESTAMP NOT NULL,
    payload JSONB NOT NULL,
    payload_checksum VARCHAR(128),
    schema_version INTEGER NOT NULL,
    import_job_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'VALID',
    error_code VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_place_source_snapshots_source_observed
    ON place_source_snapshots(source_id, observed_at DESC);

-- -----------------------------------------------------------------------------
-- Partner organizations and scoped members
-- -----------------------------------------------------------------------------
CREATE TABLE host_organizations (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    legal_name VARCHAR(500) NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    organization_type VARCHAR(24) NOT NULL DEFAULT 'BUSINESS',
    verification_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED',
    operational_status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    default_currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    contact_email VARCHAR(320),
    contact_phone VARCHAR(50),
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_host_org_verification CHECK (verification_status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED','SUSPENDED')),
    CONSTRAINT chk_host_org_status CHECK (operational_status IN ('ENABLED','DISABLED','SUSPENDED'))
);
CREATE INDEX idx_host_organizations_owner ON host_organizations(owner_user_id);

CREATE TABLE organization_members (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role_code VARCHAR(50) NOT NULL,
    member_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    invited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_organization_member UNIQUE (organization_id, user_id),
    CONSTRAINT chk_organization_member_status CHECK (member_status IN ('INVITED','ACTIVE','SUSPENDED','ACCESS_EXPIRED','DEACTIVATED'))
);
CREATE INDEX idx_organization_members_user ON organization_members(user_id, member_status);

CREATE TABLE organization_member_scopes (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL REFERENCES organization_members(id) ON DELETE CASCADE,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID,
    role_code VARCHAR(50),
    access_effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_member_resource_scope UNIQUE (membership_id, resource_type, resource_id, access_effect),
    CONSTRAINT chk_member_scope_effect CHECK (access_effect IN ('ALLOW','DENY'))
);
CREATE INDEX idx_member_scopes_membership ON organization_member_scopes(membership_id);

-- -----------------------------------------------------------------------------
-- Hotels, rooms, rates and daily inventory
-- -----------------------------------------------------------------------------
CREATE TABLE hotel_profiles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE RESTRICT,
    place_id UUID NOT NULL REFERENCES places(id) ON DELETE RESTRICT,
    property_code VARCHAR(100),
    property_type VARCHAR(32) NOT NULL DEFAULT 'HOTEL',
    star_rating SMALLINT,
    description TEXT,
    check_in_time TIME,
    check_out_time TIME,
    timezone VARCHAR(100) NOT NULL,
    amenities JSONB NOT NULL DEFAULT '[]'::jsonb,
    policies JSONB NOT NULL DEFAULT '{}'::jsonb,
    booking_contact JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    disabled_reason TEXT,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hotel_profiles_place UNIQUE (place_id),
    CONSTRAINT chk_hotel_star_rating CHECK (star_rating IS NULL OR star_rating BETWEEN 1 AND 5),
    CONSTRAINT chk_hotel_status CHECK (status IN ('DRAFT','ENABLED','DISABLED','ARCHIVED','SUSPENDED'))
);
CREATE INDEX idx_hotel_profiles_org_status ON hotel_profiles(organization_id, status);

CREATE TABLE room_types (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotel_profiles(id) ON DELETE RESTRICT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    max_adults INTEGER NOT NULL DEFAULT 1,
    max_children INTEGER NOT NULL DEFAULT 0,
    max_occupancy INTEGER NOT NULL DEFAULT 1,
    bed_config JSONB NOT NULL DEFAULT '[]'::jsonb,
    amenities JSONB NOT NULL DEFAULT '[]'::jsonb,
    images JSONB NOT NULL DEFAULT '[]'::jsonb,
    room_size_sqm DECIMAL(8,2),
    total_units INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    disabled_reason TEXT,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_room_type_code UNIQUE (hotel_id, code),
    CONSTRAINT chk_room_type_capacity CHECK (max_adults >= 1 AND max_children >= 0 AND max_occupancy >= max_adults),
    CONSTRAINT chk_room_type_units CHECK (total_units >= 0),
    CONSTRAINT chk_room_type_status CHECK (status IN ('ENABLED','DISABLED','ARCHIVED','SUSPENDED'))
);
CREATE INDEX idx_room_types_hotel_status ON room_types(hotel_id, status);

CREATE TABLE rate_plans (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE RESTRICT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(500) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    base_price DECIMAL(18,2) NOT NULL,
    meal_plan VARCHAR(32) NOT NULL DEFAULT 'ROOM_ONLY',
    cancellation_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    occupancy_pricing JSONB NOT NULL DEFAULT '{}'::jsonb,
    min_stay INTEGER NOT NULL DEFAULT 1,
    max_stay INTEGER,
    status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    data_version BIGINT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rate_plan_code UNIQUE (room_type_id, code),
    CONSTRAINT chk_rate_plan_price CHECK (base_price >= 0),
    CONSTRAINT chk_rate_plan_stay CHECK (min_stay >= 1 AND (max_stay IS NULL OR max_stay >= min_stay)),
    CONSTRAINT chk_rate_plan_status CHECK (status IN ('ENABLED','DISABLED','ARCHIVED','SUSPENDED'))
);
CREATE INDEX idx_rate_plans_room_status ON rate_plans(room_type_id, status);

CREATE TABLE room_inventory_daily (
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    inventory_date DATE NOT NULL,
    total_units INTEGER NOT NULL,
    reserved_units INTEGER NOT NULL DEFAULT 0,
    sold_units INTEGER NOT NULL DEFAULT 0,
    blocked_units INTEGER NOT NULL DEFAULT 0,
    stop_sell BOOLEAN NOT NULL DEFAULT FALSE,
    price_override DECIMAL(18,2),
    min_stay INTEGER,
    closed_to_arrival BOOLEAN NOT NULL DEFAULT FALSE,
    closed_to_departure BOOLEAN NOT NULL DEFAULT FALSE,
    data_version BIGINT NOT NULL DEFAULT 1,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (room_type_id, inventory_date),
    CONSTRAINT chk_inventory_non_negative CHECK (
        total_units >= 0 AND reserved_units >= 0 AND sold_units >= 0 AND blocked_units >= 0
        AND reserved_units + sold_units + blocked_units <= total_units
    ),
    CONSTRAINT chk_inventory_price CHECK (price_override IS NULL OR price_override >= 0)
);
CREATE INDEX idx_room_inventory_date ON room_inventory_daily(inventory_date, room_type_id);

-- -----------------------------------------------------------------------------
-- Hotel booking and payment state (provider integration remains abstracted)
-- -----------------------------------------------------------------------------
CREATE TABLE hotel_bookings (
    id UUID PRIMARY KEY,
    booking_code VARCHAR(32) NOT NULL UNIQUE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE RESTRICT,
    hotel_id UUID NOT NULL REFERENCES hotel_profiles(id) ON DELETE RESTRICT,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    adults INTEGER NOT NULL,
    children INTEGER NOT NULL DEFAULT 0,
    guest_lead JSONB NOT NULL,
    currency VARCHAR(3) NOT NULL,
    subtotal_amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    fee_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL,
    booking_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    source VARCHAR(24) NOT NULL DEFAULT 'GOROUTE',
    cancellation_reason TEXT,
    cancelled_at TIMESTAMP,
    snapshot JSONB NOT NULL,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_hotel_booking_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_hotel_booking_guests CHECK (adults >= 1 AND children >= 0),
    CONSTRAINT chk_hotel_booking_amounts CHECK (subtotal_amount >= 0 AND tax_amount >= 0 AND fee_amount >= 0 AND discount_amount >= 0 AND total_amount >= 0),
    CONSTRAINT chk_hotel_booking_status CHECK (booking_status IN ('PENDING_PAYMENT','CONFIRMED','CHECKED_IN','COMPLETED','EXPIRED','FAILED','CANCELLED_BY_GUEST','CANCELLED_BY_HOST','CANCELLED_BY_PLATFORM','NO_SHOW')),
    CONSTRAINT chk_hotel_payment_status CHECK (payment_status IN ('UNPAID','AUTHORIZED','PAID','PARTIALLY_REFUNDED','REFUNDED','FAILED','CHARGEBACK'))
);
CREATE INDEX idx_hotel_bookings_hotel_dates ON hotel_bookings(hotel_id, check_in_date, check_out_date);
CREATE INDEX idx_hotel_bookings_org_status ON hotel_bookings(organization_id, booking_status);
CREATE INDEX idx_hotel_bookings_user ON hotel_bookings(user_id, created_at DESC);

CREATE TABLE hotel_booking_items (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES hotel_bookings(id) ON DELETE CASCADE,
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE RESTRICT,
    rate_plan_id UUID NOT NULL REFERENCES rate_plans(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    adults INTEGER NOT NULL,
    children INTEGER NOT NULL DEFAULT 0,
    unit_price DECIMAL(18,2) NOT NULL,
    total_price DECIMAL(18,2) NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_booking_item_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_booking_item_price CHECK (unit_price >= 0 AND total_price >= 0)
);
CREATE INDEX idx_hotel_booking_items_booking ON hotel_booking_items(booking_id);

-- -----------------------------------------------------------------------------
-- Internal activity commerce built on the existing activity_bookings catalog
-- -----------------------------------------------------------------------------
ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS host_organization_id UUID REFERENCES host_organizations(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS place_ref_id UUID REFERENCES places(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS product_status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    ADD COLUMN IF NOT EXISTS inventory_mode VARCHAR(24) NOT NULL DEFAULT 'EXTERNAL',
    ADD COLUMN IF NOT EXISTS data_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_activity_bookings_host_status
    ON activity_bookings(host_organization_id, product_status);

CREATE TABLE activity_packages (
    id UUID PRIMARY KEY,
    activity_booking_id UUID NOT NULL REFERENCES activity_bookings(id) ON DELETE CASCADE,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    base_price DECIMAL(18,2) NOT NULL,
    min_quantity INTEGER NOT NULL DEFAULT 1,
    max_quantity INTEGER,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    cancellation_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    data_version BIGINT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_activity_package_code UNIQUE (activity_booking_id, code),
    CONSTRAINT chk_activity_package_price CHECK (base_price >= 0),
    CONSTRAINT chk_activity_package_quantity CHECK (min_quantity >= 1 AND (max_quantity IS NULL OR max_quantity >= min_quantity)),
    CONSTRAINT chk_activity_package_status CHECK (status IN ('ENABLED','DISABLED','ARCHIVED','SUSPENDED'))
);

CREATE TABLE activity_slots (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES activity_packages(id) ON DELETE CASCADE,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP,
    timezone VARCHAR(100) NOT NULL,
    capacity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    sold_quantity INTEGER NOT NULL DEFAULT 0,
    blocked_quantity INTEGER NOT NULL DEFAULT 0,
    booking_cutoff_minutes INTEGER NOT NULL DEFAULT 0,
    price_override DECIMAL(18,2),
    status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    data_version BIGINT NOT NULL DEFAULT 1,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_activity_slot_capacity CHECK (capacity >= 0 AND reserved_quantity >= 0 AND sold_quantity >= 0 AND blocked_quantity >= 0 AND reserved_quantity + sold_quantity + blocked_quantity <= capacity),
    CONSTRAINT chk_activity_slot_dates CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT chk_activity_slot_status CHECK (status IN ('ENABLED','DISABLED','CANCELLED','COMPLETED','SUSPENDED'))
);
CREATE INDEX idx_activity_slots_package_starts ON activity_slots(package_id, starts_at);

CREATE TABLE activity_orders (
    id UUID PRIMARY KEY,
    order_code VARCHAR(32) NOT NULL UNIQUE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE RESTRICT,
    activity_booking_id UUID NOT NULL REFERENCES activity_bookings(id) ON DELETE RESTRICT,
    slot_id UUID REFERENCES activity_slots(id) ON DELETE RESTRICT,
    participant_info JSONB NOT NULL DEFAULT '[]'::jsonb,
    currency VARCHAR(3) NOT NULL,
    subtotal_amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    fee_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    voucher_code VARCHAR(100),
    snapshot JSONB NOT NULL,
    data_version BIGINT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_activity_order_amounts CHECK (subtotal_amount >= 0 AND tax_amount >= 0 AND fee_amount >= 0 AND discount_amount >= 0 AND total_amount >= 0),
    CONSTRAINT chk_activity_order_status CHECK (order_status IN ('PENDING_PAYMENT','CONFIRMED','CHECKED_IN','COMPLETED','EXPIRED','FAILED','CANCELLED_BY_GUEST','CANCELLED_BY_HOST','CANCELLED_BY_PLATFORM','NO_SHOW')),
    CONSTRAINT chk_activity_order_payment_status CHECK (payment_status IN ('UNPAID','AUTHORIZED','PAID','PARTIALLY_REFUNDED','REFUNDED','FAILED','CHARGEBACK'))
);
CREATE INDEX idx_activity_orders_org_status ON activity_orders(organization_id, order_status);
CREATE INDEX idx_activity_orders_user ON activity_orders(user_id, created_at DESC);

CREATE TABLE activity_order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES activity_orders(id) ON DELETE CASCADE,
    package_id UUID NOT NULL REFERENCES activity_packages(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    total_price DECIMAL(18,2) NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_activity_order_item CHECK (quantity >= 1 AND unit_price >= 0 AND total_price >= 0)
);

-- -----------------------------------------------------------------------------
-- Shared conversation engine and review response
-- -----------------------------------------------------------------------------
CREATE TABLE marketplace_conversations (
    id UUID PRIMARY KEY,
    conversation_type VARCHAR(32) NOT NULL,
    organization_id UUID REFERENCES host_organizations(id) ON DELETE SET NULL,
    hotel_booking_id UUID REFERENCES hotel_bookings(id) ON DELETE SET NULL,
    activity_order_id UUID REFERENCES activity_orders(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    assigned_member_id UUID REFERENCES organization_members(id) ON DELETE SET NULL,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_marketplace_conversation_type CHECK (conversation_type IN ('DIRECT','HOTEL_BOOKING','ACTIVITY_ORDER')),
    CONSTRAINT chk_marketplace_conversation_status CHECK (status IN ('OPEN','PENDING','RESOLVED','CLOSED','BLOCKED','ARCHIVED')),
    CONSTRAINT chk_marketplace_conversation_target CHECK (
        (conversation_type='DIRECT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL)
        OR (conversation_type='HOTEL_BOOKING' AND hotel_booking_id IS NOT NULL AND activity_order_id IS NULL)
        OR (conversation_type='ACTIVITY_ORDER' AND hotel_booking_id IS NULL AND activity_order_id IS NOT NULL)
    )
);
CREATE INDEX idx_marketplace_conversations_org ON marketplace_conversations(organization_id, status, last_message_at DESC);

CREATE TABLE marketplace_conversation_members (
    conversation_id UUID NOT NULL REFERENCES marketplace_conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role VARCHAR(24) NOT NULL,
    last_read_message_id UUID,
    muted_until TIMESTAMP,
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    left_at TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT chk_marketplace_conversation_member_role CHECK (member_role IN ('CREATOR','PARTICIPANT','GUEST','HOST','SUPPORT','ADMIN'))
);

CREATE TABLE marketplace_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES marketplace_conversations(id) ON DELETE CASCADE,
    sender_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    client_message_id VARCHAR(100),
    message_type VARCHAR(24) NOT NULL DEFAULT 'TEXT',
    content TEXT,
    attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
    sequence_no BIGINT NOT NULL,
    edited_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_message_client UNIQUE (conversation_id, sender_user_id, client_message_id),
    CONSTRAINT uq_marketplace_message_sequence UNIQUE (conversation_id, sequence_no),
    CONSTRAINT chk_marketplace_message_type CHECK (message_type IN ('TEXT','IMAGE','FILE','SYSTEM','LOCATION')),
    CONSTRAINT chk_marketplace_message_body CHECK (content IS NOT NULL OR attachments <> '[]'::jsonb)
);
CREATE INDEX idx_marketplace_messages_conversation ON marketplace_messages(conversation_id, sequence_no);

CREATE TABLE marketplace_review_responses (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES user_reviews(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE RESTRICT,
    responder_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    response_text TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    data_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_review_response UNIQUE (review_id, organization_id),
    CONSTRAINT chk_marketplace_review_response_status CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN')),
    CONSTRAINT chk_marketplace_review_response_text CHECK (length(btrim(response_text)) > 0)
);

-- -----------------------------------------------------------------------------
-- Immutable versions and audit events
-- -----------------------------------------------------------------------------
CREATE TABLE marketplace_entity_versions (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    version_no BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    snapshot JSONB NOT NULL,
    changed_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_type VARCHAR(24) NOT NULL DEFAULT 'USER',
    reason TEXT,
    restored_from_version_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_marketplace_entity_version UNIQUE (entity_type, entity_id, version_no)
);
CREATE INDEX idx_marketplace_versions_entity ON marketplace_entity_versions(entity_type, entity_id, version_no DESC);

CREATE TABLE marketplace_audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES host_organizations(id) ON DELETE SET NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    action VARCHAR(50) NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_type VARCHAR(24) NOT NULL DEFAULT 'USER',
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_marketplace_audit_org_created ON marketplace_audit_events(organization_id, created_at DESC);
CREATE INDEX idx_marketplace_audit_entity ON marketplace_audit_events(entity_type, entity_id, created_at DESC);
