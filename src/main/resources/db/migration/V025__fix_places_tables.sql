-- Fix places and reviews tables (replacing V024 due to checksum mismatch)

-- Drop existing tables if they exist
DROP TABLE IF EXISTS place_reviews;
DROP TABLE IF EXISTS places;

-- Create places table to store Google Maps place data
CREATE TABLE places (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Google Maps IDs
    place_id VARCHAR(255) UNIQUE NOT NULL,
    cid VARCHAR(255),
    data_id VARCHAR(255),
    input_id UUID,
    
    -- Basic Info
    title VARCHAR(500) NOT NULL,
    category VARCHAR(255),
    address TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    plus_code VARCHAR(255),
    timezone VARCHAR(100),
    
    -- Contact & Web
    phone VARCHAR(50),
    website TEXT,
    google_maps_link TEXT,
    
    -- Rating & Reviews
    review_count INTEGER DEFAULT 0,
    review_rating DECIMAL(2, 1),
    reviews_per_rating TEXT, -- JSON as TEXT
    
    -- Media
    thumbnail TEXT,
    images TEXT, -- JSON as TEXT
    
    -- Details
    descriptions TEXT,
    status VARCHAR(50),
    price_range VARCHAR(50),
    
    -- Hours & Booking
    open_hours TEXT, -- JSON as TEXT
    popular_times TEXT, -- JSON as TEXT
    reservations TEXT, -- JSON as TEXT
    order_online TEXT, -- JSON as TEXT
    menu TEXT, -- JSON as TEXT
    
    -- Additional Info
    complete_address TEXT, -- JSON as TEXT
    about TEXT, -- JSON as TEXT
    owner TEXT, -- JSON as TEXT
    emails TEXT, -- JSON as TEXT
    
    -- Raw data backup
    raw_data TEXT, -- JSON as TEXT
    
    -- Metadata
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Create place_reviews table
CREATE TABLE place_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id UUID NOT NULL,
    
    -- Reviewer Info
    reviewer_name VARCHAR(255),
    profile_picture TEXT,
    
    -- Review Content
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    description TEXT,
    review_date DATE,
    
    -- Images as JSON TEXT
    images TEXT,
    
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_places_place_id ON places(place_id);
CREATE INDEX idx_places_location ON places(latitude, longitude);
CREATE INDEX idx_places_category ON places(category);
CREATE INDEX idx_places_rating ON places(review_rating);
CREATE INDEX idx_place_reviews_place ON place_reviews(place_id);
CREATE INDEX idx_place_reviews_rating ON place_reviews(rating);
CREATE INDEX idx_place_reviews_date ON place_reviews(review_date);

-- Update saved_places to reference places table
ALTER TABLE saved_places ADD COLUMN IF NOT EXISTS place_ref_id UUID;
CREATE INDEX IF NOT EXISTS idx_saved_places_place_ref ON saved_places(place_ref_id);

-- Update activities to support place reference  
ALTER TABLE activities ADD COLUMN IF NOT EXISTS place_ref_id UUID;
CREATE INDEX IF NOT EXISTS idx_activities_place_ref ON activities(place_ref_id);