-- Epic 06. A new record rather than a relaxation of `checkins`.
--
-- The existing `checkins` table requires an activity and allows one row per (user,
-- activity). Check-ins have to repeat -- visiting a place in 2024 and again in 2026 is two
-- moments, two photo sets, two passport entries -- so loosening that constraint would
-- break the semantics of activity check-in rather than extend it.
--
-- Location lives inline. Nothing here ever writes into `places`: that catalogue feeds
-- Explore, search and AI itinerary planning, and opening it to unverified user writes
-- trades a curated dataset for a permanent cleanup queue. A check-in links to a place row
-- when the spot happens to be catalogued already, and otherwise carries its own name,
-- coordinates and administrative parts.

CREATE TABLE user_checkins (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- Optional context. An activity check-in and a free check-in are the same kind of
    -- content; the only difference is whether these are set.
    trip_id             UUID REFERENCES trips (id) ON DELETE SET NULL,
    activity_id         UUID REFERENCES activities (id) ON DELETE SET NULL,
    place_id            UUID REFERENCES places (id) ON DELETE SET NULL,

    -- The review this submission created or updated, when the place was catalogued.
    -- Deleting the check-in must not delete the review, hence ON DELETE SET NULL and no
    -- cascade in the other direction.
    review_id           UUID REFERENCES user_reviews (id) ON DELETE SET NULL,

    -- Inline location (CHK-03, CHK-04).
    location_name       VARCHAR(300) NOT NULL,
    custom_name         VARCHAR(300),
    latitude            NUMERIC(10, 7) NOT NULL,
    longitude           NUMERIC(10, 7) NOT NULL,
    accuracy_meters     NUMERIC(8, 2),
    ward                VARCHAR(150),
    district            VARCHAR(150),
    province            VARCHAR(150),
    province_code       VARCHAR(10),
    location_source     VARCHAR(30) NOT NULL,

    -- Rounded-coordinate grouping key. Two check-ins at the same spot share it, across
    -- users, which is what lets Passport count distinct places and what lets an operator
    -- see "12 check-ins here, not in the catalogue yet".
    location_key        VARCHAR(40) NOT NULL,

    caption             TEXT,

    -- Ratings given at a spot that is not catalogued yet are kept here and converted into
    -- real reviews when the cluster is promoted (CHK-12). Same shape as user_reviews so
    -- the conversion is a copy, not a mapping.
    overall_rating      SMALLINT CHECK (overall_rating BETWEEN 1 AND 5),
    food_rating         SMALLINT CHECK (food_rating BETWEEN 1 AND 5),
    price_rating        SMALLINT CHECK (price_rating BETWEEN 1 AND 5),
    ambiance_rating     SMALLINT CHECK (ambiance_rating BETWEEN 1 AND 5),
    service_rating      SMALLINT CHECK (service_rating BETWEEN 1 AND 5),

    photo_source        VARCHAR(20) NOT NULL,
    visibility          VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    distance_meters     NUMERIC(10, 2),

    -- Idempotency for a repeated submit. It deliberately does NOT prevent checking in at
    -- the same place again: that is a new moment and gets its own key.
    idempotency_key     VARCHAR(120) NOT NULL,

    reward_points       INT,
    reward_reason       TEXT,

    is_removed          BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at           TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_checkin_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_user_checkin_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_user_checkin_verification
        CHECK (verification_status IN ('VERIFIED', 'UNVERIFIED', 'PENDING')),
    CONSTRAINT ck_user_checkin_photo_source
        CHECK (photo_source IN ('CAMERA', 'GALLERY', 'MIXED')),
    CONSTRAINT ck_user_checkin_location_source
        CHECK (location_source IN ('CATALOGUE_PLACE', 'TRIP_ACTIVITY', 'MAP_SEARCH',
                                   'REVERSE_GEOCODE', 'USER_NAMED', 'COORDINATES_ONLY'))
);

-- The feed reads newest-first over public, non-removed rows.
CREATE INDEX idx_user_checkins_feed ON user_checkins (created_at DESC)
    WHERE visibility = 'PUBLIC' AND is_removed = FALSE;
CREATE INDEX idx_user_checkins_user ON user_checkins (user_id, created_at DESC);
CREATE INDEX idx_user_checkins_place ON user_checkins (place_id, created_at DESC)
    WHERE place_id IS NOT NULL;
CREATE INDEX idx_user_checkins_cluster ON user_checkins (location_key);
CREATE INDEX idx_user_checkins_province ON user_checkins (user_id, province_code)
    WHERE province_code IS NOT NULL;
CREATE INDEX idx_user_checkins_trip ON user_checkins (trip_id) WHERE trip_id IS NOT NULL;

CREATE TABLE user_checkin_photos (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkin_id    UUID NOT NULL REFERENCES user_checkins (id) ON DELETE CASCADE,
    url           VARCHAR(1000) NOT NULL,
    -- Decided by the app flow, never declared by the client payload: a photo that went
    -- through the in-app camera is CAMERA, one that came from the picker is GALLERY.
    source        VARCHAR(20) NOT NULL,
    position      SMALLINT NOT NULL DEFAULT 0,
    captured_at   TIMESTAMP,
    latitude      NUMERIC(10, 7),
    longitude     NUMERIC(10, 7),
    accuracy_meters NUMERIC(8, 2),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_checkin_photo_source CHECK (source IN ('CAMERA', 'GALLERY'))
);
CREATE INDEX idx_checkin_photos_checkin ON user_checkin_photos (checkin_id, position);

-- CHK-12. A cluster only becomes a row once an operator has decided about it; until then
-- it is just the result of grouping by location_key.
CREATE TABLE checkin_cluster_decisions (
    location_key  VARCHAR(40) PRIMARY KEY,
    status        VARCHAR(20) NOT NULL,
    place_id      UUID REFERENCES places (id) ON DELETE SET NULL,
    decided_by    UUID,
    decided_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    note          TEXT,
    -- Ignoring a cluster is not permanent: it comes back if it grows substantially.
    checkin_count_at_decision INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cluster_decision_status CHECK (status IN ('PROMOTED', 'IGNORED', 'MERGED'))
);
CREATE INDEX idx_cluster_decisions_place ON checkin_cluster_decisions (place_id)
    WHERE place_id IS NOT NULL;
