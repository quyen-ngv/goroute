-- Feed ranking, phases P1-P3 (.docs/FEED_RANKING_PLAN_2026-10-22.md,
-- .docs/FEED_RANKING_DESIGN_2026-10-22.md §2).
--
-- feed_items is one flat row per check-in, review and trip that could appear in the Feed,
-- with every column the ranked feed filters and scores on, so a candidate query reads one
-- table through one index instead of joining three post tables, places and users.
--
-- It is kept current by the database, not by the services: triggers on every table that
-- changes what a row says put the post into feed_index_queue, inside the writer's own
-- transaction, and FeedIndexJob (every 30 s) re-reads the queued posts from their source
-- tables. A rolled-back write queues nothing; a restart loses nothing, because the queue is
-- a table. Interactions (likes, comments, saves, reports, hides) only flip stats_dirty; the
-- same job recounts those rows.
--
-- Additive only. Nothing that exists today reads these tables; the ranked endpoint stays off
-- (FEED.RANKING_ENABLED = false) for everyone except USER.BETA_USER until switched on.
--
-- Plain CREATE INDEX on purpose, as in V214/V232/V242: CONCURRENTLY would wait on the
-- transaction in which Flyway holds its lock.

-- ---------------------------------------------------------------------------------------
-- The province a point falls in, best source first:
--   1. the province boundary that covers it (V170 dataset);
--   2. without any boundary loaded, the active province whose centre is nearest (150 km);
--   3. the province of the nearest catalogue place within 20 km (a point just off the coast,
--      or a database with neither of the above).
-- Null for no point, or a point none of them can place.
CREATE OR REPLACE FUNCTION goroute_province_at(p_lat NUMERIC, p_lng NUMERIC)
    RETURNS VARCHAR
    LANGUAGE sql
    STABLE
AS $$
    SELECT CASE WHEN p_lat IS NULL OR p_lng IS NULL THEN NULL ELSE COALESCE(
        (SELECT pr.code
         FROM public.provinces pr
         WHERE pr.geom IS NOT NULL
           AND public.st_covers(pr.geom, public.st_setsrid(public.st_makepoint(p_lng::float8, p_lat::float8), 4326))
         LIMIT 1),
        (SELECT pr.code
         FROM public.provinces pr
         WHERE NOT EXISTS (SELECT 1 FROM public.provinces g WHERE g.geom IS NOT NULL)
           AND pr.is_active AND pr.latitude IS NOT NULL AND pr.longitude IS NOT NULL
           AND public.st_dwithin(public.goroute_point(pr.latitude::float8, pr.longitude::float8),
                                 public.goroute_point(p_lat::float8, p_lng::float8), 150000)
         ORDER BY public.st_distance(public.goroute_point(pr.latitude::float8, pr.longitude::float8),
                                     public.goroute_point(p_lat::float8, p_lng::float8))
         LIMIT 1),
        (SELECT p.province_code
         FROM public.places p
         WHERE p.province_code IS NOT NULL
           AND public.st_dwithin(public.goroute_point(p.latitude, p.longitude),
                                 public.goroute_point(p_lat::float8, p_lng::float8), 20000)
         ORDER BY public.st_distance(public.goroute_point(p.latitude, p.longitude),
                                     public.goroute_point(p_lat::float8, p_lng::float8))
         LIMIT 1)
    ) END
$$;

-- ---------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feed_items (
    item_type         VARCHAR(16)  NOT NULL CHECK (item_type IN ('CHECKIN', 'REVIEW', 'TRIP')),
    item_id           UUID         NOT NULL,
    author_id         UUID         NOT NULL,
    published_at      TIMESTAMP    NOT NULL,
    -- what the filters read
    province_code     VARCHAR(10),
    place_id          UUID,
    place_group       VARCHAR(30),
    sub_types         VARCHAR(40)[] NOT NULL DEFAULT '{}',
    tags              VARCHAR(40)[] NOT NULL DEFAULT '{}',
    point             geography(Point, 4326),
    -- what the quality score reads (set by the indexer from the source row)
    has_photo         BOOLEAN      NOT NULL DEFAULT FALSE,
    photo_count       INT          NOT NULL DEFAULT 0,
    text_len          INT          NOT NULL DEFAULT 0,
    author_trust      REAL         NOT NULL DEFAULT 50,
    -- trips only
    stop_count        INT          NOT NULL DEFAULT 0,
    linked_stop_count INT          NOT NULL DEFAULT 0,
    day_count         INT          NOT NULL DEFAULT 0,
    has_cover         BOOLEAN      NOT NULL DEFAULT FALSE,
    has_cost          BOOLEAN      NOT NULL DEFAULT FALSE,
    trip_end_date     DATE,
    stop_kind_counts  JSONB,
    -- counts (FeedIndexJob)
    like_count        INT          NOT NULL DEFAULT 0,
    comment_count     INT          NOT NULL DEFAULT 0,
    save_count        INT          NOT NULL DEFAULT 0,
    copy_count        INT          NOT NULL DEFAULT 0,
    impression_count  INT          NOT NULL DEFAULT 0,
    hide_count        INT          NOT NULL DEFAULT 0,
    report_count      INT          NOT NULL DEFAULT 0,
    -- scores (FeedIndexJob): quality Q and engagement x quality E*Q; freshness is applied per
    -- request, so nothing has to be rewritten as posts age
    quality_score     REAL         NOT NULL DEFAULT 1,
    engagement_score  REAL         NOT NULL DEFAULT 1,
    stats_dirty       BOOLEAN      NOT NULL DEFAULT TRUE,
    stats_updated_at  TIMESTAMP,
    -- state
    eligible          BOOLEAN      NOT NULL DEFAULT TRUE,
    recommend_ok      BOOLEAN      NOT NULL DEFAULT TRUE,
    indexed_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (item_type, item_id)
);

-- One index per candidate source, each ending in published_at DESC so LIMIT stops early.
CREATE INDEX IF NOT EXISTS idx_feed_items_author_pub
    ON feed_items (author_id, published_at DESC) WHERE eligible;
CREATE INDEX IF NOT EXISTS idx_feed_items_province_pub
    ON feed_items (province_code, published_at DESC) WHERE eligible;
CREATE INDEX IF NOT EXISTS idx_feed_items_place_pub
    ON feed_items (place_id, published_at DESC) WHERE eligible AND place_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_feed_items_group_pub
    ON feed_items (place_group, published_at DESC) WHERE eligible;
CREATE INDEX IF NOT EXISTS idx_feed_items_pub
    ON feed_items (published_at DESC) WHERE eligible;
CREATE INDEX IF NOT EXISTS idx_feed_items_fresh
    ON feed_items (published_at DESC) WHERE eligible AND impression_count < 30;
CREATE INDEX IF NOT EXISTS idx_feed_items_trending
    ON feed_items (engagement_score DESC) WHERE eligible AND recommend_ok;
CREATE INDEX IF NOT EXISTS idx_feed_items_point
    ON feed_items USING GIST (point) WHERE eligible;
CREATE INDEX IF NOT EXISTS idx_feed_items_sub_types
    ON feed_items USING GIN (sub_types) WHERE eligible;
CREATE INDEX IF NOT EXISTS idx_feed_items_dirty
    ON feed_items (indexed_at) WHERE stats_dirty;

-- ---------------------------------------------------------------------------------------
-- Posts whose feed_items row must be re-read from the source tables. One row per post: a
-- post written ten times between two job runs is indexed once.
CREATE TABLE IF NOT EXISTS feed_index_queue (
    item_type VARCHAR(16) NOT NULL,
    item_id   UUID        NOT NULL,
    queued_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (item_type, item_id)
);

CREATE OR REPLACE FUNCTION goroute_feed_enqueue(p_type TEXT, p_id UUID)
    RETURNS VOID
    LANGUAGE sql
AS $$
    INSERT INTO public.feed_index_queue (item_type, item_id, queued_at)
    SELECT p_type, p_id, NOW()
    WHERE p_id IS NOT NULL AND p_type IN ('CHECKIN', 'REVIEW', 'TRIP')
    ON CONFLICT (item_type, item_id) DO UPDATE SET queued_at = EXCLUDED.queued_at
$$;

-- user_checkins: the check-in itself, and the review it carries -- a review shown inside a
-- public check-in is not a Feed post of its own, so the check-in decides the review's row.
CREATE OR REPLACE FUNCTION goroute_feed_queue_checkin() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'DELETE' THEN
        PERFORM goroute_feed_enqueue('CHECKIN', NEW.id);
        PERFORM goroute_feed_enqueue('REVIEW', NEW.review_id);
    END IF;
    IF TG_OP <> 'INSERT' THEN
        PERFORM goroute_feed_enqueue('CHECKIN', OLD.id);
        IF TG_OP = 'DELETE' OR OLD.review_id IS DISTINCT FROM NEW.review_id THEN
            PERFORM goroute_feed_enqueue('REVIEW', OLD.review_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_checkin ON user_checkins;
CREATE TRIGGER trg_feed_queue_checkin
    AFTER INSERT OR UPDATE OR DELETE ON user_checkins
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_queue_checkin();

CREATE OR REPLACE FUNCTION goroute_feed_queue_review() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM goroute_feed_enqueue('REVIEW', CASE WHEN TG_OP = 'DELETE' THEN OLD.id ELSE NEW.id END);
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_review ON user_reviews;
CREATE TRIGGER trg_feed_queue_review
    AFTER INSERT OR UPDATE OR DELETE ON user_reviews
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_queue_review();

-- trips, and the two child tables that change what a trip row says (where it goes, what its
-- stops are). activity_count/memory_count updates land on trips and queue it from there.
CREATE OR REPLACE FUNCTION goroute_feed_queue_trip() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM goroute_feed_enqueue('TRIP', CASE WHEN TG_OP = 'DELETE' THEN OLD.id ELSE NEW.id END);
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_trip ON trips;
CREATE TRIGGER trg_feed_queue_trip
    AFTER INSERT OR UPDATE OR DELETE ON trips
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_queue_trip();

CREATE OR REPLACE FUNCTION goroute_feed_queue_trip_child() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM goroute_feed_enqueue('TRIP', CASE WHEN TG_OP = 'DELETE' THEN OLD.trip_id ELSE NEW.trip_id END);
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_trip_destination ON trip_destinations;
CREATE TRIGGER trg_feed_queue_trip_destination
    AFTER INSERT OR UPDATE OR DELETE ON trip_destinations
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_queue_trip_child();
DROP TRIGGER IF EXISTS trg_feed_queue_trip_activity ON activities;
CREATE TRIGGER trg_feed_queue_trip_activity
    AFTER INSERT OR UPDATE OR DELETE ON activities
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_queue_trip_child();

-- Photos added to or removed from a check-in or a review change its photo count even when
-- has_photo stays the same.
CREATE OR REPLACE FUNCTION goroute_feed_queue_media() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    r RECORD;
BEGIN
    IF TG_OP = 'DELETE' THEN r := OLD; ELSE r := NEW; END IF;
    IF r.entity_type = 'USER_CHECKIN' THEN
        PERFORM goroute_feed_enqueue('CHECKIN', r.entity_id);
    ELSIF r.entity_type = 'USER_REVIEW' THEN
        PERFORM goroute_feed_enqueue('REVIEW', r.entity_id);
    END IF;
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_media ON media_assets;
CREATE TRIGGER trg_feed_queue_media
    AFTER INSERT OR UPDATE OR DELETE ON media_assets
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_queue_media();

CREATE OR REPLACE FUNCTION goroute_feed_queue_takedown() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM goroute_feed_enqueue(NEW.content_type, NEW.content_id);
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_takedown ON content_takedowns;
CREATE TRIGGER trg_feed_queue_takedown
    AFTER INSERT OR UPDATE ON content_takedowns
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_queue_takedown();

-- A deleted account takes its posts out; a restored one brings them back.
CREATE OR REPLACE FUNCTION goroute_feed_queue_user() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO public.feed_index_queue (item_type, item_id, queued_at)
    SELECT fi.item_type, fi.item_id, NOW() FROM public.feed_items fi WHERE fi.author_id = NEW.id
    ON CONFLICT (item_type, item_id) DO UPDATE SET queued_at = EXCLUDED.queued_at;
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_user ON users;
CREATE TRIGGER trg_feed_queue_user
    AFTER UPDATE OF deleted_at ON users
    FOR EACH ROW WHEN (OLD.deleted_at IS DISTINCT FROM NEW.deleted_at)
    EXECUTE FUNCTION goroute_feed_queue_user();

-- A place re-classified or moved changes the group, sub types, point and province of every
-- check-in and review on it.
CREATE OR REPLACE FUNCTION goroute_feed_queue_place() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO public.feed_index_queue (item_type, item_id, queued_at)
    SELECT fi.item_type, fi.item_id, NOW() FROM public.feed_items fi WHERE fi.place_id = NEW.id
    ON CONFLICT (item_type, item_id) DO UPDATE SET queued_at = EXCLUDED.queued_at;
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS trg_feed_queue_place ON places;
CREATE TRIGGER trg_feed_queue_place
    AFTER UPDATE OF place_group, sub_types, latitude, longitude, province_code ON places
    FOR EACH ROW
    WHEN (OLD.place_group IS DISTINCT FROM NEW.place_group
       OR OLD.sub_types IS DISTINCT FROM NEW.sub_types
       OR OLD.latitude IS DISTINCT FROM NEW.latitude
       OR OLD.longitude IS DISTINCT FROM NEW.longitude
       OR OLD.province_code IS DISTINCT FROM NEW.province_code)
    EXECUTE FUNCTION goroute_feed_queue_place();

-- ---------------------------------------------------------------------------------------
-- Interactions: only mark the post's counts stale. One UPDATE by primary key per write.
CREATE OR REPLACE FUNCTION goroute_feed_mark_dirty(p_type TEXT, p_id UUID)
    RETURNS VOID
    LANGUAGE sql
AS $$
    UPDATE public.feed_items SET stats_dirty = TRUE
    WHERE item_type = p_type AND item_id = p_id AND NOT stats_dirty
$$;

CREATE OR REPLACE FUNCTION goroute_feed_dirty_from_interaction() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    r RECORD;
BEGIN
    IF TG_OP = 'DELETE' THEN r := OLD; ELSE r := NEW; END IF;
    IF TG_TABLE_NAME = 'user_checkin_likes' THEN
        PERFORM goroute_feed_mark_dirty('CHECKIN', r.checkin_id);
    ELSIF TG_TABLE_NAME = 'review_helpful_votes' THEN
        PERFORM goroute_feed_mark_dirty('REVIEW', r.review_id);
    ELSIF TG_TABLE_NAME = 'trip_helpful_votes' THEN
        PERFORM goroute_feed_mark_dirty('TRIP', r.trip_id);
    ELSIF TG_TABLE_NAME = 'content_comments' THEN
        PERFORM goroute_feed_mark_dirty(r.content_type, r.content_id);
    ELSIF TG_TABLE_NAME = 'saved_posts' THEN
        PERFORM goroute_feed_mark_dirty(r.post_type, r.post_id);
    ELSIF TG_TABLE_NAME = 'content_reports' THEN
        PERFORM goroute_feed_mark_dirty(r.content_type, r.content_id);
    ELSIF TG_TABLE_NAME = 'feed_feedback' THEN
        IF r.kind = 'HIDE_POST' THEN
            PERFORM goroute_feed_mark_dirty(r.item_type, r.item_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_feed_dirty_checkin_like ON user_checkin_likes;
CREATE TRIGGER trg_feed_dirty_checkin_like AFTER INSERT OR DELETE ON user_checkin_likes
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_dirty_from_interaction();
DROP TRIGGER IF EXISTS trg_feed_dirty_review_vote ON review_helpful_votes;
CREATE TRIGGER trg_feed_dirty_review_vote AFTER INSERT OR UPDATE OR DELETE ON review_helpful_votes
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_dirty_from_interaction();
DROP TRIGGER IF EXISTS trg_feed_dirty_trip_vote ON trip_helpful_votes;
CREATE TRIGGER trg_feed_dirty_trip_vote AFTER INSERT OR UPDATE OR DELETE ON trip_helpful_votes
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_dirty_from_interaction();
DROP TRIGGER IF EXISTS trg_feed_dirty_comment ON content_comments;
CREATE TRIGGER trg_feed_dirty_comment AFTER INSERT OR UPDATE OR DELETE ON content_comments
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_dirty_from_interaction();
DROP TRIGGER IF EXISTS trg_feed_dirty_saved_post ON saved_posts;
CREATE TRIGGER trg_feed_dirty_saved_post AFTER INSERT OR DELETE ON saved_posts
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_dirty_from_interaction();
DROP TRIGGER IF EXISTS trg_feed_dirty_report ON content_reports;
CREATE TRIGGER trg_feed_dirty_report AFTER INSERT OR UPDATE OR DELETE ON content_reports
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_dirty_from_interaction();
DROP TRIGGER IF EXISTS trg_feed_dirty_feedback ON feed_feedback;
CREATE TRIGGER trg_feed_dirty_feedback AFTER INSERT OR UPDATE ON feed_feedback
    FOR EACH ROW EXECUTE FUNCTION goroute_feed_dirty_from_interaction();

-- ---------------------------------------------------------------------------------------
-- Indexes the recounts and the SOCIAL source need.
CREATE INDEX IF NOT EXISTS idx_saved_posts_post ON saved_posts (post_type, post_id);
CREATE INDEX IF NOT EXISTS idx_feed_impressions_item ON feed_impressions (item_type, item_id);
CREATE INDEX IF NOT EXISTS idx_content_comments_user_created
    ON content_comments (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_helpful_votes_user_created
    ON review_helpful_votes (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trip_helpful_votes_user_created
    ON trip_helpful_votes (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_saved_posts_user_created_desc
    ON saved_posts (user_id, created_at DESC);

-- ---------------------------------------------------------------------------------------
-- How much a viewer cares about an author (P3), from 90 days of likes, comments, saves and
-- opened posts, decayed by age. Rebuilt every night by FeedAffinityJob.
CREATE TABLE IF NOT EXISTS user_author_affinity (
    viewer_id  UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_id  UUID      NOT NULL,
    score      REAL      NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (viewer_id, author_id)
);

-- ---------------------------------------------------------------------------------------
-- Backfill: queue every post; the first job runs index them through the same code path as
-- every later change.
INSERT INTO feed_index_queue (item_type, item_id)
SELECT 'CHECKIN', id FROM user_checkins
ON CONFLICT DO NOTHING;
INSERT INTO feed_index_queue (item_type, item_id)
SELECT 'REVIEW', id FROM user_reviews
ON CONFLICT DO NOTHING;
INSERT INTO feed_index_queue (item_type, item_id)
SELECT 'TRIP', id FROM trips WHERE is_deleted = FALSE
ON CONFLICT DO NOTHING;

INSERT INTO config (label, key, value, description, is_active)
VALUES ('FEED', 'RANKING_ENABLED', 'false',
        'Bat Feed xep hang (GET /feed/for-you) cho moi nguoi; khi tat chi USER.BETA_USER va nhom FEED.RANKING_PERCENT dung', TRUE),
       ('FEED', 'RANKING_PERCENT', '0',
        'Phan tram nguoi dung (theo hash user id) dung Feed xep hang khi RANKING_ENABLED tat (0-100)', TRUE),
       ('FEED', 'RANKING_WEIGHTS', '{}',
        'JSON ghi de trong so xep hang Feed; khoa thieu lay mac dinh trong code (xem .docs/FEED_RANKING_DESIGN_2026-10-22.md §3.7)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
