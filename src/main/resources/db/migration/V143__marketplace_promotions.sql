-- Rate plan promotions: early-bird / last-minute / long-stay / basic discounts applied per night.
-- Exactly one promotion may apply to a night (highest percent wins); they never stack.

CREATE TABLE IF NOT EXISTS rate_plan_promotions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES host_organizations(id) ON DELETE CASCADE,
    hotel_id UUID NOT NULL REFERENCES hotel_profiles(id) ON DELETE CASCADE,
    -- NULL means "every rate plan of this hotel"
    rate_plan_id UUID REFERENCES rate_plans(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    promotion_type VARCHAR(24) NOT NULL DEFAULT 'BASIC',
    discount_percent NUMERIC(5,2) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    stay_start DATE,
    stay_end DATE,
    book_start DATE,
    book_end DATE,
    min_advance_days INT,
    max_advance_days INT,
    min_nights INT,
    days_of_week JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
    data_version BIGINT NOT NULL DEFAULT 1,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_promotion_type CHECK (promotion_type IN ('BASIC','EARLY_BIRD','LAST_MINUTE','LONG_STAY')),
    CONSTRAINT chk_promotion_status CHECK (status IN ('ENABLED','DISABLED','ARCHIVED')),
    CONSTRAINT chk_promotion_percent CHECK (discount_percent > 0 AND discount_percent <= 90),
    CONSTRAINT chk_promotion_stay_range CHECK (stay_end IS NULL OR stay_start IS NULL OR stay_end >= stay_start),
    CONSTRAINT chk_promotion_book_range CHECK (book_end IS NULL OR book_start IS NULL OR book_end >= book_start),
    CONSTRAINT chk_promotion_advance CHECK (max_advance_days IS NULL OR min_advance_days IS NULL OR max_advance_days >= min_advance_days),
    CONSTRAINT uq_rate_plan_promotion_code UNIQUE (hotel_id, code)
);

CREATE INDEX IF NOT EXISTS idx_rate_plan_promotions_hotel ON rate_plan_promotions(hotel_id, status);
CREATE INDEX IF NOT EXISTS idx_rate_plan_promotions_rate ON rate_plan_promotions(rate_plan_id) WHERE rate_plan_id IS NOT NULL;
