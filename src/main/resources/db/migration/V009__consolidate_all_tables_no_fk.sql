-- GoRoute Database Schema - Consolidated (No Foreign Keys, Uppercase Enums)
-- All tables in one migration, no constraints between tables
-- Relationships managed in application code

-- ============ USERS & AUTH ============
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(255) NOT NULL,
    username VARCHAR(100) UNIQUE,
    avatar_url VARCHAR(500),
    provider VARCHAR(20) DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    default_currency VARCHAR(10) DEFAULT 'VND',
    default_travel_mode VARCHAR(20) DEFAULT 'DRIVING',
    location_tracking VARCHAR(20) DEFAULT 'IN_TRIP',
    auto_checkin_radius INT DEFAULT 50,
    budget_alert_threshold INT DEFAULT 80,
    budget_alert_daily BOOLEAN DEFAULT TRUE,
    default_trip_visibility VARCHAR(20) DEFAULT 'PRIVATE',
    language VARCHAR(10) DEFAULT 'vi',
    theme VARCHAR(10) DEFAULT 'system',
    onboarding_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    fcm_token VARCHAR(500) NOT NULL,
    device_type VARCHAR(20) NOT NULL,
    device_name VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, fcm_token)
);

-- ============ TRIPS ============
CREATE TABLE IF NOT EXISTS trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    cover_image_url VARCHAR(500),
    destination VARCHAR(255),
    destination_place_id VARCHAR(255),
    destination_lat DECIMAL(10,7),
    destination_lng DECIMAL(10,7),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    budget DECIMAL(15,2),
    currency VARCHAR(10) DEFAULT 'VND',
    status VARCHAR(20) DEFAULT 'PLANNING',
    visibility VARCHAR(20) DEFAULT 'PRIVATE',
    share_code VARCHAR(20) UNIQUE,
    timezone VARCHAR(50),
    owner_id UUID NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trip_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) DEFAULT 'EDITOR',
    status VARCHAR(20) DEFAULT 'PENDING',
    invited_by UUID,
    joined_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(trip_id, user_id)
);

-- ============ PLACES & ACTIVITIES ============
CREATE TABLE IF NOT EXISTS custom_places (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    category VARCHAR(50),
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    day_number INT NOT NULL,
    sort_order INT NOT NULL,
    place_id VARCHAR(255),
    custom_place_id UUID,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    start_time TIME,
    end_time TIME,
    estimated_cost DECIMAL(15,2),
    cost_currency VARCHAR(10),
    category VARCHAR(50),
    rating DECIMAL(2,1),
    photo_url VARCHAR(500),
    notes TEXT,
    status VARCHAR(20) DEFAULT 'CONFIRMED',
    transport_mode VARCHAR(20),
    is_accommodation BOOLEAN DEFAULT FALSE,
    is_starting_point BOOLEAN DEFAULT FALSE,
    starting_point_date TIMESTAMP,
    added_by UUID,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS place_cache (
    place_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    address VARCHAR(500),
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    rating DECIMAL(2,1),
    total_ratings INT,
    price_level INT,
    types VARCHAR(100)[],
    opening_hours JSONB,
    photo_refs JSONB,
    description TEXT,
    cached_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP DEFAULT NOW() + INTERVAL '30 days'
);

CREATE TABLE IF NOT EXISTS saved_places (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    place_id VARCHAR(255),
    custom_place_id UUID,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    category VARCHAR(50),
    rating DECIMAL(2,1),
    photo_url VARCHAR(500),
    tags VARCHAR(255)[],
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, place_id)
);

-- ============ EXPENSES & CHECK-INS ============
CREATE TABLE IF NOT EXISTS expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    activity_id UUID,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    exchange_rate DECIMAL(15,6),
    amount_in_trip_currency DECIMAL(15,2),
    category VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    paid_by UUID NOT NULL,
    receipt_url VARCHAR(500),
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS expense_splits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id UUID NOT NULL,
    user_id UUID NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    is_settled BOOLEAN DEFAULT FALSE,
    settled_at TIMESTAMP,
    UNIQUE(expense_id, user_id)
);

CREATE TABLE IF NOT EXISTS checkins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL,
    user_id UUID NOT NULL,
    checked_in_at TIMESTAMP DEFAULT NOW(),
    rating INT CHECK (rating >= 1 AND rating <= 5),
    notes TEXT,
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    auto_checkin BOOLEAN DEFAULT FALSE,
    UNIQUE(activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS checkin_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkin_id UUID NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trip_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    activity_id UUID,
    uploaded_by UUID NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    day_number INT,
    taken_at TIMESTAMP,
    lat DECIMAL(10,7),
    lng DECIMAL(10,7),
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============ NOTIFICATIONS & ACTIVITY LOG ============
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    trip_id UUID,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    data JSONB,
    actor_id UUID,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS activity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    user_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    data JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trip_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    doc_date DATE,
    file_url VARCHAR(500) NOT NULL,
    file_size INT,
    file_type VARCHAR(20),
    uploaded_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS packing_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    category VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    is_checked BOOLEAN DEFAULT FALSE,
    is_shared BOOLEAN DEFAULT FALSE,
    checked_by UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS activity_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL,
    user_id UUID NOT NULL,
    vote VARCHAR(10) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS activity_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id UUID NOT NULL,
    user_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============ TRIP NOTES ============
CREATE TABLE IF NOT EXISTS trip_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- ============ INDEXES (No Foreign Keys) ============
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices(user_id, is_active);

CREATE INDEX IF NOT EXISTS idx_trips_owner ON trips(owner_id);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips(status);
CREATE INDEX IF NOT EXISTS idx_trips_dates ON trips(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_trips_share_code ON trips(share_code);
CREATE INDEX IF NOT EXISTS idx_trip_members_user ON trip_members(user_id, status);
CREATE INDEX IF NOT EXISTS idx_trip_members_trip ON trip_members(trip_id, status);

CREATE INDEX IF NOT EXISTS idx_activities_trip_day ON activities(trip_id, day_number, sort_order);
CREATE INDEX IF NOT EXISTS idx_activities_place ON activities(place_id);
CREATE INDEX IF NOT EXISTS idx_place_cache_expires ON place_cache(expires_at);
CREATE INDEX IF NOT EXISTS idx_saved_places_user ON saved_places(user_id, category);

CREATE INDEX IF NOT EXISTS idx_expenses_trip ON expenses(trip_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_expenses_paid_by ON expenses(paid_by);
CREATE INDEX IF NOT EXISTS idx_expense_splits_user ON expense_splits(user_id, is_settled);
CREATE INDEX IF NOT EXISTS idx_checkins_activity ON checkins(activity_id);
CREATE INDEX IF NOT EXISTS idx_trip_photos_trip ON trip_photos(trip_id, day_number);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_log_trip ON activity_log(trip_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trip_documents_trip ON trip_documents(trip_id, type);
CREATE INDEX IF NOT EXISTS idx_packing_items_trip ON packing_items(trip_id, category);
CREATE INDEX IF NOT EXISTS idx_activity_votes_activity ON activity_votes(activity_id);
CREATE INDEX IF NOT EXISTS idx_activity_comments_activity ON activity_comments(activity_id, created_at);
CREATE INDEX IF NOT EXISTS idx_trip_notes_trip ON trip_notes(trip_id);
