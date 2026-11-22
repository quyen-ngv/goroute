-- Radius search on PostGIS instead of a trig formula per row, plus the indexes the Feed,
-- review lists and place lists were missing. See .docs/BACKEND_PERF_PLAN_2026-10-07.md.
--
-- Plain CREATE INDEX on purpose: CREATE INDEX CONCURRENTLY waits for every open
-- transaction, including the one in which Flyway holds its advisory lock, so a
-- concurrent build would hang the migration.

-- One point constructor for every coordinate pair. Queries must call it exactly as the
-- index expressions below do (goroute_point(lat, lng)) for the planner to use the index.
-- Double precision parameters: numeric columns cast to it implicitly. Schema-qualified
-- PostGIS calls keep the function valid under any search_path (pg_dump restores).
CREATE OR REPLACE FUNCTION goroute_point(lat double precision, lng double precision)
    RETURNS geography
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS $$
    SELECT public.st_setsrid(public.st_makepoint(lng, lat), 4326)::public.geography
$$;

CREATE INDEX IF NOT EXISTS idx_places_point
    ON places USING GIST (goroute_point(latitude, longitude));

CREATE INDEX IF NOT EXISTS idx_user_checkins_point
    ON user_checkins USING GIST (goroute_point(latitude, longitude));

CREATE INDEX IF NOT EXISTS idx_user_reviews_checkin_point
    ON user_reviews USING GIST (goroute_point(checkin_lat, checkin_lng))
    WHERE checkin_lat IS NOT NULL AND checkin_lng IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trip_destinations_point
    ON trip_destinations USING GIST (goroute_point(lat, lng))
    WHERE lat IS NOT NULL AND lng IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trips_destination_point
    ON trips USING GIST (goroute_point(destination_lat, destination_lng))
    WHERE destination_lat IS NOT NULL AND destination_lng IS NOT NULL;

-- Review feed anti-join: "this review is already shown through its check-in".
CREATE INDEX IF NOT EXISTS idx_user_checkins_review_public
    ON user_checkins (review_id)
    WHERE review_id IS NOT NULL AND visibility = 'PUBLIC' AND is_removed = FALSE;

-- Admin/type-ahead place list ordered by newest.
CREATE INDEX IF NOT EXISTS idx_places_created_id ON places (created_at DESC, id DESC);

-- A place's own review list, newest first.
CREATE INDEX IF NOT EXISTS idx_user_reviews_place_created ON user_reviews (place_id, created_at DESC);

-- A place's imported Google reviews in display order.
CREATE INDEX IF NOT EXISTS idx_place_reviews_place_rank
    ON place_reviews (place_id, authenticity_score DESC NULLS LAST, review_date DESC, id DESC)
    WHERE is_deleted IS NOT TRUE;

-- City Story feed: "which stories did this user like".
CREATE INDEX IF NOT EXISTS idx_city_story_likes_user ON city_story_likes (user_id, created_at);

-- Indexes no query can use. Each costs a write on every full-row place update.
DROP INDEX IF EXISTS idx_places_location;           -- exact duplicate of idx_places_lat_lng
DROP INDEX IF EXISTS idx_places_fulltext;           -- no query uses to_tsvector
DROP INDEX IF EXISTS idx_places_ai_description;     -- no query uses to_tsvector
DROP INDEX IF EXISTS idx_place_translations_name_trgm; -- no query matches translated names in SQL; Lucene does
