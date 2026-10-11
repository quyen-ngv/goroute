-- Epic 06: Mapbox as a second map provider beside Goong, chosen per cohort, with deep caching
-- and a ceiling on what the system may spend. Additive only: new config rows, new tables, and a
-- wider CHECK on the per-user usage counter.

-- 1. Which provider answers. Beta users (USER/BETA_USER) start on Mapbox; everybody else stays
--    on Goong until an operator switches USER_PROVIDER.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('MAP_PROVIDER', 'USER_PROVIDER', 'GOONG', 'Nha cung cap ban do cho user thuong (tim kiem, geocode, duong di): GOONG hoac MAPBOX', TRUE),
    ('MAP_PROVIDER', 'BETA_PROVIDER', 'MAPBOX', 'Nha cung cap ban do cho beta user (USER/BETA_USER): GOONG hoac MAPBOX', TRUE),
    ('MAP_PROVIDER', 'FALLBACK_TO_GOONG', 'true', 'Mapbox loi thi goi lai bang Goong (chi trong Viet Nam); true/false', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- 2. Per-user limits for the two map calls that had none.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('MAP_SEARCH', 'GEOCODE_PER_MINUTE', '20', 'So lan geocode (tra nguoc toa do, tim thanh pho) toi da moi user trong 1 phut (1-600)', TRUE),
    ('MAP_SEARCH', 'GEOCODE_PER_DAY', '200', 'So lan geocode toi da moi user trong 1 ngay (1-100000)', TRUE),
    ('MAP_SEARCH', 'ROUTE_PER_MINUTE', '30', 'So lan tinh duong di (1 ngay lich trinh) toi da moi user trong 1 phut (1-600)', TRUE),
    ('MAP_SEARCH', 'ROUTE_PER_DAY', '300', 'So lan tinh duong di toi da moi user trong 1 ngay (1-100000)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- 3. Ceiling on paid Mapbox calls. The caps equal Mapbox's free tier: at the cap the server
--    stops calling Mapbox and answers from caches until the period ends.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('MAP_BUDGET', 'ENABLED', 'true', 'Bat tran ngan sach goi Mapbox toan he thong; true/false', TRUE),
    ('MAP_BUDGET', 'SEARCH_MONTHLY_CAP', '50000', 'So request Mapbox Search Box toi da moi thang (free tier 50000)', TRUE),
    ('MAP_BUDGET', 'GEOCODE_MONTHLY_CAP', '100000', 'So request Mapbox Geocoding toi da moi thang (free tier 100000)', TRUE),
    ('MAP_BUDGET', 'DIRECTIONS_MONTHLY_CAP', '100000', 'So request Mapbox Directions toi da moi thang (free tier 100000)', TRUE),
    ('MAP_BUDGET', 'DAILY_CAP_PERCENT', '10', 'Moi ngay duoc dung toi da bao nhieu % tran thang (1-100)', TRUE),
    ('MAP_BUDGET', 'WARN_PERCENT', '80', 'Bao admin khi dung qua bao nhieu % tran thang (1-100)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- 4. The per-user counter now also counts geocoding and routes.
ALTER TABLE map_search_usage_daily DROP CONSTRAINT IF EXISTS chk_map_search_usage_operation;
ALTER TABLE map_search_usage_daily ADD CONSTRAINT chk_map_search_usage_operation
    CHECK (operation IN ('AUTOCOMPLETE', 'PLACE_DETAIL', 'GEOCODE', 'ROUTE'));

-- 5. Every place a provider has told us about, kept for good (Mapbox allows storing results).
--    A place-detail call for a known ID is answered from here. picked_count > 0 marks places a
--    user actually chose; autocomplete searches those before asking the provider.
CREATE TABLE IF NOT EXISTS map_place_cache (
    provider          VARCHAR(16)      NOT NULL,
    provider_place_id VARCHAR(255)     NOT NULL,
    name              VARCHAR(500)     NOT NULL,
    name_normalized   VARCHAR(500)     NOT NULL,
    address           VARCHAR(1000),
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    categories        VARCHAR(500),
    phone             VARCHAR(64),
    website           VARCHAR(500),
    country_code      VARCHAR(8),
    picked_count      INTEGER          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (provider, provider_place_id),
    CONSTRAINT chk_map_place_cache_provider CHECK (provider IN ('GOONG', 'MAPBOX'))
);

CREATE INDEX IF NOT EXISTS idx_map_place_cache_picked_name
    ON map_place_cache USING gin (name_normalized gin_trgm_ops)
    WHERE picked_count > 0;

-- 6. Whole answers, already in the app's (Goong) shape, keyed by a hash of the normalised
--    request: autocomplete by query + rounded position + language, reverse by a ~50 m grid cell.
CREATE TABLE IF NOT EXISTS map_response_cache (
    cache_key  CHAR(64)    PRIMARY KEY,
    operation  VARCHAR(32) NOT NULL,
    provider   VARCHAR(16) NOT NULL,
    body       TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_map_response_cache_expires ON map_response_cache(expires_at);

-- 7. Paid calls the whole system made, per month and per day (UTC), for the budget ceiling.
CREATE TABLE IF NOT EXISTS map_provider_usage (
    period_kind   VARCHAR(8)  NOT NULL,
    period_start  DATE        NOT NULL,
    api           VARCHAR(16) NOT NULL,
    call_count    BIGINT      NOT NULL DEFAULT 0,
    refused_count BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (period_kind, period_start, api),
    CONSTRAINT chk_map_provider_usage_kind CHECK (period_kind IN ('DAY', 'MONTH')),
    CONSTRAINT chk_map_provider_usage_api CHECK (api IN ('SEARCH', 'GEOCODE', 'DIRECTIONS'))
);

-- 8. One road leg between two points for one profile, shared by every user and trip. Points are
--    rounded to 5 decimals (~1 m) in the key, so the same two catalogue places always hit it.
CREATE TABLE IF NOT EXISTS route_leg_cache (
    leg_key      CHAR(64)         PRIMARY KEY,
    provider     VARCHAR(16)      NOT NULL,
    profile      VARCHAR(16)      NOT NULL,
    from_lat     DOUBLE PRECISION NOT NULL,
    from_lng     DOUBLE PRECISION NOT NULL,
    to_lat       DOUBLE PRECISION NOT NULL,
    to_lng       DOUBLE PRECISION NOT NULL,
    distance_m   INTEGER          NOT NULL,
    duration_s   INTEGER          NOT NULL,
    polyline     TEXT             NOT NULL,
    created_at   TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_route_leg_cache_last_used ON route_leg_cache(last_used_at);

-- 9. The Mapbox token the app draws tiles with. Empty = the app keeps the token built into it.
--    Exposed through PUBLIC_CONFIG so rotating the token needs no app release (apps from the
--    route-cache release on read it at start and use it for every map after).
INSERT INTO config (label, key, value, description, is_active)
VALUES ('MAPBOX', 'APP_TILE_TOKEN', '', 'Token Mapbox (pk.) app dung de ve ban do; de trong = dung token co san trong app', TRUE)
ON CONFLICT (label, key) DO NOTHING;

UPDATE config
SET value = value || ', {label=''MAPBOX'',key=''APP_TILE_TOKEN''}',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'CONFIG' AND key = 'PUBLIC_CONFIG'
  AND value NOT LIKE '%APP_TILE_TOKEN%';
