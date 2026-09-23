-- Administrative boundaries: provinces gain geometry, wards appear, and check-ins,
-- places and tourist areas can point at a ward.
--
-- Two things this migration deliberately does NOT do, because both need the boundary
-- dataset loaded first (scripts/geo/README.md) and Flyway cannot wait for psql:
--   * it does not replace the 63 old provinces with the 34 current ones -- the SQL the
--     script emits upserts the 34, and the admin backfill (POST /admin/geo/backfill)
--     remaps every province_code and retires the rows that no longer exist;
--   * it does not fill places.ward_code / user_checkins.ward_code -- same backfill.
-- Until the backfill runs everything keeps working exactly as before: no column added
-- here is read by a code path that cannot cope with it being NULL.

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- 1. Provinces: the 34-province dataset carries names in two languages and a shape
-- ---------------------------------------------------------------------------
ALTER TABLE provinces
    ADD COLUMN IF NOT EXISTS name_en      VARCHAR(120),
    ADD COLUMN IF NOT EXISTS full_name    VARCHAR(160),
    ADD COLUMN IF NOT EXISTS full_name_en VARCHAR(160),
    ADD COLUMN IF NOT EXISTS code_name    VARCHAR(80),
    ADD COLUMN IF NOT EXISTS area_km2     NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS geom         geometry(MultiPolygon, 4326),
    ADD COLUMN IF NOT EXISTS geom_display geometry(MultiPolygon, 4326),
    ADD COLUMN IF NOT EXISTS bbox         geometry(Polygon, 4326);
CREATE INDEX IF NOT EXISTS idx_provinces_geom ON provinces USING GIST (geom);

-- Which former province each retired code merged into. Filled by the dataset SQL;
-- read by the backfill to move passport_events, wishes, passport scopes and places.
CREATE TABLE IF NOT EXISTS province_code_remap (
    old_code VARCHAR(10) PRIMARY KEY,
    new_code VARCHAR(10) NOT NULL,
    old_name VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS geo_dataset_versions (
    version        VARCHAR(20) PRIMARY KEY,
    decree         VARCHAR(40),
    generated_at   TIMESTAMP,
    imported_at    TIMESTAMP NOT NULL DEFAULT now(),
    backfilled_at  TIMESTAMP,
    province_count INT,
    ward_count     INT
);

-- ---------------------------------------------------------------------------
-- 2. Wards (xã / phường / đặc khu). No district tier exists any more.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wards (
    code            VARCHAR(10) PRIMARY KEY,
    province_code   VARCHAR(10) NOT NULL REFERENCES provinces (code) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    name_en         VARCHAR(120),
    full_name       VARCHAR(160) NOT NULL,
    full_name_en    VARCHAR(160),
    code_name       VARCHAR(80),
    unit_type       VARCHAR(20) NOT NULL,
    postal_code     VARCHAR(10),
    area_km2        NUMERIC(10, 2),
    dataset_version VARCHAR(20) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    -- geom: source resolution, for point-in-polygon. geom_display: simplified, served to
    -- the app. Two columns because a 30 m tolerance is fine to draw and wrong to verify.
    geom            geometry(MultiPolygon, 4326) NOT NULL,
    geom_display    geometry(MultiPolygon, 4326) NOT NULL,
    CONSTRAINT ck_ward_unit_type CHECK (unit_type IN ('WARD', 'COMMUNE', 'SPECIAL_ZONE'))
);
CREATE INDEX IF NOT EXISTS idx_wards_geom ON wards USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_wards_province ON wards (province_code);

-- ---------------------------------------------------------------------------
-- 3. Place: the ward it sits in, and an optional drawn verification area
-- ---------------------------------------------------------------------------
-- verification_geometry replaces verification_radius_meters when present: a lake or an
-- old quarter is not a circle. Kept nullable; the radius stays the default.
ALTER TABLE places
    ADD COLUMN IF NOT EXISTS ward_code             VARCHAR(10) REFERENCES wards (code) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS verification_geometry geometry(MultiPolygon, 4326);
CREATE INDEX IF NOT EXISTS idx_places_ward ON places (ward_code) WHERE ward_code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. Check-in: which ward, and at which level the location was proven
-- ---------------------------------------------------------------------------
-- verification_scope refines verification_status rather than replacing it:
--   PLACE  -- inside the place's area/radius (the badge everyone already knows)
--   WARD   -- not at the place, but provably inside a ward (the escalation)
--   NONE   -- unverified
-- Escalation stops at the ward. A province is too large to be evidence of anything.
ALTER TABLE user_checkins
    ADD COLUMN IF NOT EXISTS ward_code          VARCHAR(10),
    ADD COLUMN IF NOT EXISTS verification_scope VARCHAR(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE user_checkins
    DROP CONSTRAINT IF EXISTS ck_user_checkin_scope;
ALTER TABLE user_checkins
    ADD CONSTRAINT ck_user_checkin_scope CHECK (verification_scope IN ('PLACE', 'WARD', 'NONE'));
CREATE INDEX IF NOT EXISTS idx_user_checkins_user_ward
    ON user_checkins (user_id, ward_code) WHERE ward_code IS NOT NULL;

-- Rows that were VERIFIED before this migration were verified at the place: that was
-- the only rule that existed.
UPDATE user_checkins SET verification_scope = 'PLACE'
WHERE verification_status = 'VERIFIED' AND verification_scope = 'NONE';

ALTER TABLE passport_events
    ADD COLUMN IF NOT EXISTS ward_code VARCHAR(10);
CREATE INDEX IF NOT EXISTS idx_passport_events_user_ward
    ON passport_events (user_id, ward_code) WHERE ward_code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. Tourist area <-> wards. An area such as "Phố cổ Hà Nội" is several wards, and a
--    ward match is objective where the coverage radius was a guess.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS location_image_wards (
    location_image_id UUID NOT NULL REFERENCES location_images (id) ON DELETE CASCADE,
    ward_code         VARCHAR(10) NOT NULL REFERENCES wards (code) ON DELETE CASCADE,
    PRIMARY KEY (location_image_id, ward_code)
);
CREATE INDEX IF NOT EXISTS idx_location_image_wards_ward ON location_image_wards (ward_code);

-- Resolver used by the location-area auto-map: an area that lists the point's ward
-- wins outright; otherwise fall back to the nearest area whose radius reaches it.
--
-- Nothing stops two areas listing the same ward ("Phố cổ Hà Nội" and "Hà Nội" both
-- covering Hoàn Kiếm), so the tie-break is stated rather than left to the planner:
-- the area made of fewer wards is the more specific answer, then the one whose anchor
-- is nearer, then the id so the result never wobbles between runs.
CREATE OR REPLACE FUNCTION goroute_location_image_for_point(p_lat NUMERIC, p_lng NUMERIC)
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(
        (SELECT liw.location_image_id
         FROM wards w
         JOIN location_image_wards liw ON liw.ward_code = w.code
         LEFT JOIN location_images li ON li.id = liw.location_image_id
         WHERE p_lat IS NOT NULL AND p_lng IS NOT NULL
           AND ST_Covers(w.geom, ST_SetSRID(ST_MakePoint(p_lng::float8, p_lat::float8), 4326))
         ORDER BY (SELECT COUNT(*) FROM location_image_wards peer
                   WHERE peer.location_image_id = liw.location_image_id),
                  CASE
                      WHEN li.latitude IS NULL OR li.longitude IS NULL THEN NULL
                      ELSE ST_Distance(
                          ST_SetSRID(ST_MakePoint(li.longitude::float8, li.latitude::float8), 4326)::geography,
                          ST_SetSRID(ST_MakePoint(p_lng::float8, p_lat::float8), 4326)::geography)
                  END NULLS LAST,
                  liw.location_image_id
         LIMIT 1),
        goroute_nearest_location_image(p_lat, p_lng))
$$;

-- ---------------------------------------------------------------------------
-- 6. Config and permissions
-- ---------------------------------------------------------------------------
INSERT INTO config (label, key, value, description, is_active) VALUES
    ('CHECKIN', 'REWARD_WARD_VERIFIED_MULTIPLIER', '0.7',
     'He so thuong khi check-in chi xac thuc duoc o cap xa/phuong, khong o dung dia diem (0..5)', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('geo', 'get'),
    ('geo', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'geo'
WHERE r.code IN ('SUPER_ADMIN', 'OPERATIONS')
ON CONFLICT DO NOTHING;
