-- Enable pg_trgm extension for fuzzy text search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create location_images table
CREATE TABLE location_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_address TEXT NOT NULL,
    normalized_address TEXT NOT NULL,
    image_url TEXT NOT NULL,
    priority INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create trigram index for fast fuzzy search
CREATE INDEX idx_location_images_normalized ON location_images USING gin(normalized_address gin_trgm_ops);

-- Create index for priority ordering
CREATE INDEX idx_location_images_priority ON location_images(priority DESC);

-- Sample data for testing
INSERT INTO location_images (full_address, normalized_address, image_url, priority) VALUES
('Đà Nẵng', 'da nang', 'https://images.unsplash.com/photo-1583417319070-4a69db38a482', 50),
('Hà Nội', 'ha noi', 'https://images.unsplash.com/photo-1509023464722-18d996393ca8', 50),
('Hồ Chí Minh', 'ho chi minh', 'https://images.unsplash.com/photo-1583417319070-4a69db38a482', 50);
