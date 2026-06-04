-- Create activity_bookings table for tour catalog (Klook/Viator)
CREATE TABLE activity_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(255),
    source VARCHAR(50), -- 'KLOOK', 'VIATOR'
    url TEXT,

    -- Basic Info
    title VARCHAR(500) NOT NULL,
    description TEXT,

    -- Location
    activity_address VARCHAR(500),
    departing_from VARCHAR(255),
    navigation_list JSONB, -- ["Vietnam", "Tam Coc - Bich Dong"]
    itinerary_stops JSONB, -- ["Hanam province", "Hoa Lu Ancient Capital"]
    pickup_addresses JSONB, -- ["Hanoi Opera House"]

    -- Pricing
    price_amount DECIMAL(10,2),
    price_currency VARCHAR(3) DEFAULT 'USD',

    -- Stats
    duration_raw VARCHAR(100),
    duration_hours DECIMAL(4,1),
    rating DECIMAL(3,2),
    review_count INT,
    booked_count INT,

    -- Media & Content (JSON)
    thumbnail TEXT,
    images JSONB, -- Array of image URLs
    highlights JSONB, -- Array of highlight strings
    what_to_expect JSONB, -- Array of {text, image}
    itinerary JSONB, -- Array of {title, content, images[]}

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_activity_bookings_external_id ON activity_bookings(external_id);
CREATE INDEX idx_activity_bookings_source ON activity_bookings(source);
CREATE INDEX idx_activity_bookings_departing_from ON activity_bookings(departing_from);
CREATE INDEX idx_activity_bookings_itinerary_stops ON activity_bookings USING GIN(itinerary_stops);
CREATE INDEX idx_activity_bookings_navigation_list ON activity_bookings USING GIN(navigation_list);
CREATE INDEX idx_activity_bookings_rating ON activity_bookings(rating);
CREATE INDEX idx_activity_bookings_price ON activity_bookings(price_amount);

-- Add booking reference to activities table
ALTER TABLE activities ADD COLUMN booking_id UUID REFERENCES activity_bookings(id) ON DELETE SET NULL;
ALTER TABLE activities ADD COLUMN booking_source VARCHAR(50);

CREATE INDEX idx_activities_booking_id ON activities(booking_id);
