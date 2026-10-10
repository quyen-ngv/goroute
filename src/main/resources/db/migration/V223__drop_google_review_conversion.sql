-- Undo V221 (Google review -> check-in conversion). The feature was dropped after V221 had
-- already run in production; its code is gone, so this removes everything it could have left.
--
-- Safe whether or not POST /v1/api/admin/place-reviews/convert-to-checkins was ever called
-- with dryRun=false: with no converted rows every statement below matches nothing.
-- Storage objects are not touched here; the restored reviews point at the same files the
-- converted posts showed.

-- 1. The Google reviews that became posts go back to place_reviews. Their photos are still
--    in MinIO (the conversion kept them for the posts). The reviewers' profile pictures were
--    deleted from MinIO, so profile_picture comes back empty. One row per Google review: a
--    rerun can have archived the same review twice, and only the first copy is the one whose
--    photos the post kept (the rerun deleted the re-imported copy's files).
INSERT INTO place_reviews (
    id, place_id, review_id, google_place_id, reviewer_name, profile_url, profile_picture,
    is_local_guide, total_reviews, total_photos, rating, description, language, review_date,
    images, likes, content_hash, is_deleted, authenticity_score, authenticity_level,
    score_calculated_at, created_at, updated_at
)
SELECT DISTINCT ON (COALESCE(a.review_id, a.id::text))
       a.id, a.place_id, a.review_id, a.google_place_id, a.reviewer_name, a.profile_url, NULL,
       a.is_local_guide, a.total_reviews, a.total_photos, a.rating, a.description, a.language,
       a.review_date, a.images, a.likes, a.content_hash, a.is_deleted, a.authenticity_score,
       a.authenticity_level, a.score_calculated_at, a.created_at, a.updated_at
FROM place_reviews_archive a
JOIN places p ON p.id = a.place_id
WHERE a.converted_checkin_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM place_reviews pr
      WHERE pr.id = a.id OR (a.review_id IS NOT NULL AND pr.review_id = a.review_id)
  )
ORDER BY COALESCE(a.review_id, a.id::text), a.archived_at ASC;

-- 2. Places whose TripMind score counted a placeholder's review, for step 6.
CREATE TEMP TABLE v223_affected_places ON COMMIT DROP AS
SELECT DISTINCT r.place_id
FROM user_reviews r
JOIN imported_reviewers ir ON ir.user_id = r.user_id
WHERE r.place_id IS NOT NULL;

-- 3. Comments real users left on the placeholders' posts (polymorphic, no foreign key).
DELETE FROM content_comments cc
WHERE (cc.content_type = 'CHECKIN' AND cc.content_id IN (
          SELECT c.id FROM user_checkins c JOIN imported_reviewers ir ON ir.user_id = c.user_id))
   OR (cc.content_type = 'REVIEW' AND cc.content_id IN (
          SELECT r.id FROM user_reviews r JOIN imported_reviewers ir ON ir.user_id = r.user_id));

-- 4. Photo rows of those posts (no foreign key). The files stay: step 1 points at them again.
DELETE FROM media_assets m
WHERE m.entity_type IN ('USER_CHECKIN', 'USER_REVIEW')
  AND m.uploaded_by IN (SELECT user_id FROM imported_reviewers);

-- 5. The placeholder users. ON DELETE CASCADE takes their check-ins, reviews, likes,
--    follows and their imported_reviewers rows with them.
DELETE FROM users
WHERE id IN (SELECT user_id FROM imported_reviewers);

-- 6. Scores of the affected places: drop the row where no review is left (the app then
--    falls back to the Google score, as before the conversion); otherwise correct the count.
--    The weighted score itself is recomputed by the next review written there.
DELETE FROM place_scores ps
USING v223_affected_places a
WHERE ps.place_id = a.place_id
  AND NOT EXISTS (SELECT 1 FROM user_reviews r WHERE r.place_id = ps.place_id);

UPDATE place_scores ps
SET review_count = (SELECT COUNT(*) FROM user_reviews r WHERE r.place_id = ps.place_id)
FROM v223_affected_places a
WHERE ps.place_id = a.place_id;

-- 7. V221 switched the scraper's daily review refresh off; put it back.
UPDATE config
SET value = 'true'
WHERE label = 'PLACE_REVIEW' AND key = 'DAILY_REFRESH_ENABLED';

DROP TABLE IF EXISTS place_reviews_archive;
DROP TABLE IF EXISTS imported_reviewers;
