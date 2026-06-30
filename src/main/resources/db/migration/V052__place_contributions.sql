-- Place contribution system (user-submitted Google Maps URLs + pending reviews)

CREATE TABLE place_contribution_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_url_hash VARCHAR(64) NOT NULL,
    google_maps_url TEXT NOT NULL,
    place_name_hint VARCHAR(500),
    resolved_google_place_id VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    linked_place_id UUID,
    scrape_job_id VARCHAR(255),
    goroute_job_id UUID,
    admin_note TEXT,
    rejection_reason TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_pcg_url_hash ON place_contribution_groups(normalized_url_hash);
CREATE INDEX idx_pcg_status ON place_contribution_groups(status);
CREATE INDEX idx_pcg_resolved_gplace ON place_contribution_groups(resolved_google_place_id);

CREATE TABLE place_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
    google_maps_url TEXT NOT NULL,
    place_name_hint VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, group_id)
);

CREATE INDEX idx_place_contributions_user ON place_contributions(user_id);
CREATE INDEX idx_place_contributions_group ON place_contributions(group_id);

CREATE TABLE pending_contribution_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_id UUID NOT NULL UNIQUE,
    overall_rating SMALLINT NOT NULL CHECK (overall_rating BETWEEN 1 AND 5),
    food_rating SMALLINT CHECK (food_rating IS NULL OR food_rating BETWEEN 1 AND 5),
    price_rating SMALLINT CHECK (price_rating IS NULL OR price_rating BETWEEN 1 AND 5),
    ambiance_rating SMALLINT CHECK (ambiance_rating IS NULL OR ambiance_rating BETWEEN 1 AND 5),
    service_rating SMALLINT CHECK (service_rating IS NULL OR service_rating BETWEEN 1 AND 5),
    text TEXT,
    photos TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE place_contributors (
    place_id UUID NOT NULL,
    user_id UUID NOT NULL,
    contribution_id UUID,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (place_id, user_id)
);

CREATE INDEX idx_place_contributors_place ON place_contributors(place_id);

CREATE TABLE place_contribution_import_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_group_id UUID NOT NULL UNIQUE,
    goroute_job_id UUID UNIQUE,
    scrape_job_id VARCHAR(255),
    goroute_place_id UUID,
    place_already_exists BOOLEAN NOT NULL DEFAULT false,
    reviews_published INT NOT NULL DEFAULT 0,
    contributors_added INT NOT NULL DEFAULT 0,
    processed_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_places_cid ON places(cid) WHERE cid IS NOT NULL;
