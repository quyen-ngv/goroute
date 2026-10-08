-- Feed ordering without a per-row EXISTS over media_assets, and the indexes that let the
-- check-in and review feeds page by keyset. See .docs/BACKEND_PERF_PLAN_2026-10-07.md
-- Phase 7.
--
-- Both feeds order "posts with photos first, then newest". Until now "with photos" was an
-- EXISTS over media_assets evaluated for every candidate row, so the database had to sort
-- the whole public table on every page. has_photo stores that answer on the post itself.
--
-- Plain CREATE INDEX on purpose: CREATE INDEX CONCURRENTLY waits for every open
-- transaction, including the one in which Flyway holds its advisory lock.

ALTER TABLE user_checkins ADD COLUMN IF NOT EXISTS has_photo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_reviews ADD COLUMN IF NOT EXISTS has_photo BOOLEAN NOT NULL DEFAULT FALSE;

-- The two definitions differ on purpose: they are exactly the EXISTS each feed used before.
-- A check-in counts only photo rows; a review counts any live media row.
UPDATE user_checkins c
SET has_photo = TRUE
WHERE EXISTS (
    SELECT 1 FROM media_assets ma
    WHERE ma.entity_type = 'USER_CHECKIN'
      AND ma.entity_id = c.id
      AND (ma.asset_role IS NULL OR ma.asset_role = 'PHOTO')
      AND ma.deleted_at IS NULL);

UPDATE user_reviews r
SET has_photo = TRUE
WHERE EXISTS (
    SELECT 1 FROM media_assets ma
    WHERE ma.entity_type = 'USER_REVIEW'
      AND ma.entity_id = r.id
      AND ma.deleted_at IS NULL);

-- The keyset cursor is (has_photo, created_at, id); a NULL created_at cannot be compared.
-- The only insert path leaves the column to its NOW() default, so this backfill is a guard.
UPDATE user_reviews SET created_at = COALESCE(updated_at, NOW()) WHERE created_at IS NULL;
ALTER TABLE user_reviews ALTER COLUMN created_at SET NOT NULL;

-- Kept current by the database rather than by every code path that writes media_assets:
-- photos are attached, re-ordered and soft-deleted from the check-in flow, the review
-- repository, storage cleanup, moderation and the admin console, and one missed path
-- would leave a post sorted in the wrong group for good.
CREATE OR REPLACE FUNCTION goroute_refresh_post_has_photo(p_entity_type TEXT, p_entity_id UUID)
    RETURNS VOID
    LANGUAGE plpgsql
AS $$
DECLARE
    v_has_photo BOOLEAN;
BEGIN
    IF p_entity_id IS NULL THEN
        RETURN;
    END IF;
    IF p_entity_type = 'USER_CHECKIN' THEN
        v_has_photo := EXISTS (
            SELECT 1 FROM public.media_assets ma
            WHERE ma.entity_type = 'USER_CHECKIN'
              AND ma.entity_id = p_entity_id
              AND (ma.asset_role IS NULL OR ma.asset_role = 'PHOTO')
              AND ma.deleted_at IS NULL);
        UPDATE public.user_checkins
        SET has_photo = v_has_photo
        WHERE id = p_entity_id AND has_photo IS DISTINCT FROM v_has_photo;
    ELSIF p_entity_type = 'USER_REVIEW' THEN
        v_has_photo := EXISTS (
            SELECT 1 FROM public.media_assets ma
            WHERE ma.entity_type = 'USER_REVIEW'
              AND ma.entity_id = p_entity_id
              AND ma.deleted_at IS NULL);
        UPDATE public.user_reviews
        SET has_photo = v_has_photo
        WHERE id = p_entity_id AND has_photo IS DISTINCT FROM v_has_photo;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION goroute_media_assets_sync_has_photo()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM public.goroute_refresh_post_has_photo(NEW.entity_type, NEW.entity_id);
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM public.goroute_refresh_post_has_photo(OLD.entity_type, OLD.entity_id);
    ELSE
        PERFORM public.goroute_refresh_post_has_photo(OLD.entity_type, OLD.entity_id);
        IF NEW.entity_type IS DISTINCT FROM OLD.entity_type
                OR NEW.entity_id IS DISTINCT FROM OLD.entity_id THEN
            PERFORM public.goroute_refresh_post_has_photo(NEW.entity_type, NEW.entity_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_media_assets_post_has_photo_ins ON media_assets;
CREATE TRIGGER trg_media_assets_post_has_photo_ins
    AFTER INSERT ON media_assets
    FOR EACH ROW
    WHEN (NEW.entity_type IN ('USER_CHECKIN', 'USER_REVIEW'))
    EXECUTE FUNCTION goroute_media_assets_sync_has_photo();

DROP TRIGGER IF EXISTS trg_media_assets_post_has_photo_upd ON media_assets;
CREATE TRIGGER trg_media_assets_post_has_photo_upd
    AFTER UPDATE OF entity_type, entity_id, asset_role, deleted_at ON media_assets
    FOR EACH ROW
    WHEN (OLD.entity_type IN ('USER_CHECKIN', 'USER_REVIEW')
          OR NEW.entity_type IN ('USER_CHECKIN', 'USER_REVIEW'))
    EXECUTE FUNCTION goroute_media_assets_sync_has_photo();

DROP TRIGGER IF EXISTS trg_media_assets_post_has_photo_del ON media_assets;
CREATE TRIGGER trg_media_assets_post_has_photo_del
    AFTER DELETE ON media_assets
    FOR EACH ROW
    WHEN (OLD.entity_type IN ('USER_CHECKIN', 'USER_REVIEW'))
    EXECUTE FUNCTION goroute_media_assets_sync_has_photo();

-- The public check-in feed: findFeed's filter and ORDER BY, so a page (and a keyset
-- cursor) is a range read with no sort.
CREATE INDEX IF NOT EXISTS idx_user_checkins_feed_photo
    ON user_checkins (has_photo DESC, created_at DESC, id DESC)
    WHERE visibility = 'PUBLIC' AND is_removed = FALSE;

-- The review feed shows a review only when it has text or a photo; the predicate is
-- spelled exactly as findFeedReviews spells it so the planner can match it.
CREATE INDEX IF NOT EXISTS idx_user_reviews_feed_photo
    ON user_reviews (has_photo DESC, created_at DESC, id DESC)
    WHERE (NULLIF(TRIM(COALESCE(text, '')), '') IS NOT NULL OR has_photo);
