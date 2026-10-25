-- Backfill: one check-in for every user review written before reviews were created from
-- check-ins (08-checkin-passport §2.7). Today a rated check-in at a catalogue place
-- creates/updates the review and stores its id in user_checkins.review_id; older reviews
-- have no check-in behind them, so they show up as bare review posts and are missing from
-- the author's check-in history.
--
-- Mirrors UserCheckinServiceImpl.create for a CATALOGUE_PLACE check-in:
--   location_key = 'p:' || place_id, visibility PUBLIC, location_source CATALOGUE_PLACE,
--   ward/province resolved from PostGIS wards like CheckinVerifier (district cleared),
--   photos copied as media_assets rows (entity_type USER_CHECKIN, asset_role PHOTO).
-- Deliberately NOT done here (the Java listeners do these, SQL cannot run their rules):
--   no Star reward, no passport_events / stamps, no follower fan-out or push.
-- created_at / visited_at = the review's created_at, so the posts land at their original
-- date in the Feed instead of on top of it.
--
-- Re-runnable: a review that already has a check-in (review_id) is never picked again, and
-- idempotency_key = 'review-backfill:<review id>' is unique per user.
--
-- How to run (psql or DBeaver, one session):
--   1. Run the whole file. It stops inside an open transaction.
--   2. Read the "preview" and "result" outputs.
--   3. COMMIT;   -- or ROLLBACK; to undo everything.

BEGIN;

-- ===== Options ==========================================================================
-- require_photo            A live check-in needs at least one photo (CreateUserCheckinRequest).
--                          TRUE skips reviews without a photo; FALSE creates photo-less check-ins.
-- include_booking_reviews  Reviews written from a hotel booking / activity order. The booking
--                          flow still creates those without a check-in, so FALSE leaves them.
CREATE TEMP TABLE review_backfill_opts ON COMMIT DROP AS
SELECT TRUE  AS require_photo,
       FALSE AS include_booking_reviews;

-- ===== 1. Candidates (read-only) ========================================================
CREATE TEMP TABLE review_backfill ON COMMIT DROP AS
WITH base AS (
    SELECT r.*,
           p.title                        AS place_name,
           p.latitude                     AS place_lat,
           p.longitude                    AS place_lng,
           u.deleted_at                   AS author_deleted_at,
           (SELECT COUNT(*) FROM media_assets ma
            WHERE ma.entity_type = 'USER_REVIEW'
              AND ma.entity_id = r.id
              AND ma.deleted_at IS NULL)  AS photo_count,
           -- Use where the author stood when the review recorded it; otherwise the place.
           (r.checkin_lat IS NOT NULL AND r.checkin_lng IS NOT NULL) AS has_review_point
    FROM user_reviews r
    JOIN users u ON u.id = r.user_id
    LEFT JOIN places p ON p.id = r.place_id
)
SELECT b.id                                                         AS review_id,
       b.user_id,
       b.place_id,
       b.trip_id,
       gen_random_uuid()                                            AS checkin_id,
       LEFT(COALESCE(NULLIF(BTRIM(b.place_name), ''), 'Unknown place'), 300) AS location_name,
       CASE WHEN b.has_review_point THEN b.checkin_lat ELSE b.place_lat END AS latitude,
       CASE WHEN b.has_review_point THEN b.checkin_lng ELSE b.place_lng END AS longitude,
       CASE WHEN b.has_review_point THEN b.checkin_accuracy_m END   AS accuracy_meters,
       CASE WHEN b.has_review_point AND b.place_lat IS NOT NULL AND b.place_lng IS NOT NULL
            THEN ROUND(ST_Distance(
                     ST_SetSRID(ST_MakePoint(b.checkin_lng::float8, b.checkin_lat::float8), 4326)::geography,
                     ST_SetSRID(ST_MakePoint(b.place_lng::float8, b.place_lat::float8), 4326)::geography
                 )::numeric, 2)
       END                                                          AS distance_meters,
       -- location_verified was set by the same place check the verifier runs; anything else
       -- had no camera photo, which the verifier always calls UNVERIFIED / NONE.
       CASE WHEN b.location_verified THEN 'VERIFIED' ELSE 'UNVERIFIED' END AS verification_status,
       CASE WHEN b.location_verified THEN 'PLACE' ELSE 'NONE' END   AS verification_scope,
       NULLIF(BTRIM(b.text), '')                                    AS caption,
       b.overall_rating, b.food_rating, b.price_rating, b.ambiance_rating, b.service_rating,
       b.created_at,
       COALESCE(b.updated_at, b.created_at)                         AS updated_at,
       b.photo_count,
       CASE
           WHEN EXISTS (SELECT 1 FROM user_checkins c WHERE c.review_id = b.id)
               THEN 'already_has_checkin'          -- includes check-ins the author deleted
           WHEN b.place_id IS NULL
               THEN 'no_place'                     -- activity-booking review
           WHEN (b.hotel_booking_id IS NOT NULL OR b.activity_order_id IS NOT NULL)
                AND NOT o.include_booking_reviews
               THEN 'booking_review'
           WHEN b.author_deleted_at IS NOT NULL
               THEN 'author_deleted'
           WHEN EXISTS (SELECT 1 FROM content_takedowns t
                        WHERE t.content_type = 'REVIEW' AND t.content_id = b.id
                          AND t.restored_at IS NULL)
               THEN 'taken_down'
           WHEN EXISTS (SELECT 1 FROM user_checkins c
                        WHERE c.user_id = b.user_id AND c.place_id = b.place_id
                          AND c.is_removed = FALSE)
               THEN 'user_has_other_checkin_here'  -- see the optional block at the end
           WHEN b.photo_count = 0 AND o.require_photo
               THEN 'no_photo'
           WHEN NOT b.has_review_point AND (b.place_lat IS NULL OR b.place_lng IS NULL)
               THEN 'no_coordinates'
       END                                                          AS skip_reason
FROM base b
CROSS JOIN review_backfill_opts o;

-- Ward / province, as CheckinVerifier writes them: official names, district dropped.
ALTER TABLE review_backfill
    ADD COLUMN ward_code VARCHAR(10),
    ADD COLUMN ward VARCHAR(150),
    ADD COLUMN province VARCHAR(150),
    ADD COLUMN province_code VARCHAR(10);

UPDATE review_backfill b
SET ward_code     = w.code,
    ward          = w.full_name,
    province      = pr.name,
    province_code = w.province_code
FROM wards w
JOIN provinces pr ON pr.code = w.province_code
WHERE b.skip_reason IS NULL
  AND w.is_active = TRUE
  AND ST_Covers(w.geom, ST_SetSRID(ST_MakePoint(b.longitude::float8, b.latitude::float8), 4326));

-- Preview: what will be created, and why the rest is left alone.
SELECT COALESCE(skip_reason, '>> will_create') AS outcome,
       COUNT(*)                                AS reviews,
       COUNT(DISTINCT user_id)                 AS users,
       SUM(photo_count)                        AS photos
FROM review_backfill
GROUP BY 1
ORDER BY 1;

-- Sample of what will be written.
SELECT review_id, user_id, place_id, location_name, latitude, longitude, ward, province,
       verification_status, overall_rating, photo_count, created_at, LEFT(caption, 60) AS caption
FROM review_backfill
WHERE skip_reason IS NULL
ORDER BY created_at
LIMIT 50;

-- ===== 2. Write =========================================================================
INSERT INTO user_checkins
    (id, user_id, trip_id, activity_id, place_id, review_id, location_name, custom_name,
     latitude, longitude, accuracy_meters, ward, district, province, province_code,
     location_source, location_key, caption, frame_style,
     overall_rating, food_rating, price_rating, ambiance_rating, service_rating,
     photo_source, visibility, verification_status, verification_scope, ward_code,
     distance_meters, idempotency_key, is_removed, visited_at, created_at, updated_at)
SELECT b.checkin_id, b.user_id, b.trip_id, NULL, b.place_id, b.review_id, b.location_name, NULL,
       b.latitude, b.longitude, b.accuracy_meters, b.ward, NULL, b.province, b.province_code,
       'CATALOGUE_PLACE', 'p:' || b.place_id, b.caption, NULL,
       b.overall_rating, b.food_rating, b.price_rating, b.ambiance_rating, b.service_rating,
       -- Review photos came from the picker, never the in-app camera.
       'GALLERY', 'PUBLIC', b.verification_status, b.verification_scope, b.ward_code,
       b.distance_meters, 'review-backfill:' || b.review_id, FALSE, b.created_at, b.created_at, b.updated_at
FROM review_backfill b
WHERE b.skip_reason IS NULL
ON CONFLICT (user_id, idempotency_key) DO NOTHING;

-- Photos: a copy of each live review photo row, pointing at the new check-in. The object
-- itself is shared; the storage sweep only deletes it once no row references the URL.
-- has_photo on the check-in is set by trg_media_assets_post_has_photo_ins.
INSERT INTO media_assets
    (id, trip_id, activity_id, entity_type, entity_id, media_type, url, asset_role,
     position, caption, description, taken_at, latitude, longitude, accuracy_meters,
     capture_source, date_source, place_id, location_name, location_source,
     uploaded_by, created_at, updated_at)
SELECT gen_random_uuid(), c.trip_id, NULL, 'USER_CHECKIN', c.id, 'IMAGE', ma.url, 'PHOTO',
       (ROW_NUMBER() OVER (PARTITION BY c.id
                           ORDER BY ma.position NULLS LAST, ma.created_at, ma.id) - 1)::smallint,
       ma.caption, ma.description, ma.taken_at, ma.latitude, ma.longitude, ma.accuracy_meters,
       'GALLERY', CASE WHEN ma.taken_at IS NULL THEN 'UPLOAD' ELSE 'CAPTURE' END,
       c.place_id, c.location_name, c.location_source,
       c.user_id, ma.created_at, ma.created_at
FROM review_backfill b
JOIN user_checkins c ON c.id = b.checkin_id          -- only check-ins this run inserted
JOIN media_assets ma ON ma.entity_type = 'USER_REVIEW'
                    AND ma.entity_id = b.review_id
                    AND ma.deleted_at IS NULL
WHERE b.skip_reason IS NULL;

-- places.checkin_count (V225) is recounted by a job within ~30 s / nightly; do it now for
-- the touched places so Home ranking does not wait for the night run.
UPDATE places p
SET checkin_count = (SELECT COUNT(*) FROM user_checkins c
                     WHERE c.place_id = p.id AND c.is_removed = FALSE)
WHERE p.id IN (SELECT b.place_id FROM review_backfill b
               JOIN user_checkins c ON c.id = b.checkin_id);

-- Result.
SELECT COUNT(*)                                  AS checkins_created,
       COUNT(*) FILTER (WHERE c.has_photo)       AS with_photo,
       COUNT(DISTINCT c.user_id)                 AS users,
       COUNT(*) FILTER (WHERE c.ward_code IS NULL) AS without_ward
FROM review_backfill b
JOIN user_checkins c ON c.id = b.checkin_id;

-- Reviews of a catalogue place that still have no check-in, by reason.
SELECT b.skip_reason, COUNT(*)
FROM review_backfill b
WHERE NOT EXISTS (SELECT 1 FROM user_checkins c WHERE c.review_id = b.review_id)
  AND b.place_id IS NOT NULL
GROUP BY 1
ORDER BY 2 DESC;

-- ===== Optional: user_has_other_checkin_here ============================================
-- The author already has a check-in at that place (one made without a rating, before
-- check-ins carried reviews). Creating another would put a second post about the same
-- visit in the Feed. To attach the review to their latest unlinked check-in there instead,
-- uncomment and run before COMMIT:
--
-- UPDATE user_checkins c
-- SET review_id = b.review_id, updated_at = NOW()
-- FROM review_backfill b
-- WHERE b.skip_reason = 'user_has_other_checkin_here'
--   AND c.id = (SELECT c2.id FROM user_checkins c2
--               WHERE c2.user_id = b.user_id AND c2.place_id = b.place_id
--                 AND c2.is_removed = FALSE AND c2.review_id IS NULL
--               ORDER BY c2.visited_at DESC, c2.created_at DESC
--               LIMIT 1);

-- Now: COMMIT;  or  ROLLBACK;
