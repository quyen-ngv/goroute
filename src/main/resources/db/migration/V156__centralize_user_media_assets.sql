-- Hard cutover for user-owned images.
--
-- The application version that ships with this migration reads and writes only
-- media_assets. The legacy columns/tables are used here as migration sources,
-- never as a runtime fallback.

ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS asset_role VARCHAR(40),
    ADD COLUMN IF NOT EXISTS position SMALLINT,
    ADD COLUMN IF NOT EXISTS accuracy_meters NUMERIC(8, 2);

CREATE INDEX IF NOT EXISTS idx_media_assets_entity_position
    ON media_assets (entity_type, entity_id, position, created_at)
    WHERE deleted_at IS NULL;

-- Rows created by V136 did not have a role or position. They are the expense
-- photo list, so make that meaning explicit before inserting receipts/new rows.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at, id) - 1 AS photo_position
    FROM media_assets
    WHERE entity_type = 'EXPENSE'
      AND asset_role IS NULL
      AND deleted_at IS NULL
)
UPDATE media_assets m
SET asset_role = 'PHOTO',
    position = ranked.photo_position
FROM ranked
WHERE m.id = ranked.id;

UPDATE media_assets
SET asset_role = 'MEMORY'
WHERE entity_type IN ('TRIP_MEMORY', 'TRIP_ACTIVITY_MEMORY')
  AND asset_role IS NULL
  AND deleted_at IS NULL;

-- Expense photo_urls -> central assets.
INSERT INTO media_assets (
    id, trip_id, activity_id, entity_type, entity_id, media_type, url,
    asset_role, position, uploaded_by, created_at, updated_at
)
SELECT gen_random_uuid(),
       e.trip_id,
       e.activity_id,
       'EXPENSE',
       e.id,
       'IMAGE',
       btrim(photo.url),
       'PHOTO',
       (photo.position - 1)::smallint,
       COALESCE(e.created_by, e.paid_by),
       COALESCE(e.created_at, NOW()),
       NOW()
FROM expenses e
CROSS JOIN LATERAL unnest(e.photo_urls) WITH ORDINALITY AS photo(url, position)
WHERE e.photo_urls IS NOT NULL
  AND photo.url IS NOT NULL
  AND btrim(photo.url) <> ''
  AND COALESCE(e.created_by, e.paid_by) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM media_assets m
      WHERE m.entity_type = 'EXPENSE'
        AND m.entity_id = e.id
        AND m.asset_role = 'PHOTO'
        AND m.url = btrim(photo.url)
  );

-- Expense receipt_url was not part of the old response, but it is still an
-- image reference and must be owned by the common table for cleanup and future
-- responses.
INSERT INTO media_assets (
    id, trip_id, activity_id, entity_type, entity_id, media_type, url,
    asset_role, position, uploaded_by, created_at, updated_at
)
SELECT gen_random_uuid(),
       e.trip_id,
       e.activity_id,
       'EXPENSE',
       e.id,
       'IMAGE',
       btrim(e.receipt_url),
       'RECEIPT',
       0,
       COALESCE(e.created_by, e.paid_by),
       COALESCE(e.created_at, NOW()),
       NOW()
FROM expenses e
WHERE e.receipt_url IS NOT NULL
  AND btrim(e.receipt_url) <> ''
  AND COALESCE(e.created_by, e.paid_by) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM media_assets m
      WHERE m.entity_type = 'EXPENSE'
        AND m.entity_id = e.id
        AND m.asset_role = 'RECEIPT'
        AND m.url = btrim(e.receipt_url)
  );

-- Check-in photos keep their ids so edit/delete/replay semantics do not change.
INSERT INTO media_assets (
    id, trip_id, activity_id, entity_type, entity_id, media_type, url,
    asset_role, position, caption, description, taken_at,
    latitude, longitude, accuracy_meters, capture_source, date_source,
    place_id, location_name, location_source, uploaded_by, created_at, updated_at
)
SELECT p.id,
       c.trip_id,
       c.activity_id,
       'USER_CHECKIN',
       c.id,
       'IMAGE',
       p.url,
       'PHOTO',
       p.position,
       p.title,
       p.description,
       p.captured_at,
       p.latitude,
       p.longitude,
       p.accuracy_meters,
       p.source,
       CASE WHEN p.captured_at IS NULL THEN 'UPLOAD' ELSE 'CAPTURE' END,
       c.place_id,
       c.location_name,
       c.location_source,
       c.user_id,
       p.created_at,
       p.created_at
FROM user_checkin_photos p
JOIN user_checkins c ON c.id = p.checkin_id
WHERE NOT EXISTS (SELECT 1 FROM media_assets m WHERE m.id = p.id);

-- User reviews store a JSON array of URLs in older versions. WITH ORDINALITY
-- preserves the old gallery order in the new position column.
INSERT INTO media_assets (
    id, trip_id, entity_type, entity_id, media_type, url,
    asset_role, position, place_id, uploaded_by, created_at, updated_at
)
SELECT gen_random_uuid(),
       r.trip_id,
       'USER_REVIEW',
       r.id,
       'IMAGE',
       btrim(photo.url),
       'PHOTO',
       (photo.position - 1)::smallint,
       r.place_id,
       r.user_id,
       COALESCE(r.created_at, NOW()),
       NOW()
FROM user_reviews r
CROSS JOIN LATERAL jsonb_array_elements_text(
    CASE
        WHEN LEFT(BTRIM(COALESCE(r.photos, '')), 1) = '['
         AND RIGHT(BTRIM(COALESCE(r.photos, '')), 1) = ']'
            THEN BTRIM(r.photos)::jsonb
        ELSE '[]'::jsonb
    END
)
    WITH ORDINALITY AS photo(url, position)
WHERE photo.url IS NOT NULL
  AND btrim(photo.url) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM media_assets m
      WHERE m.entity_type = 'USER_REVIEW'
        AND m.entity_id = r.id
        AND m.url = btrim(photo.url)
  );

-- Pending contribution reviews are still user-authored media. They are kept
-- separate from published user_reviews until the contribution is accepted.
INSERT INTO media_assets (
    id, entity_type, entity_id, media_type, url, asset_role, position,
    place_id, uploaded_by, created_at, updated_at
)
SELECT gen_random_uuid(),
       'PENDING_REVIEW',
       r.id,
       'IMAGE',
       btrim(photo.url),
       'PHOTO',
       (photo.position - 1)::smallint,
       NULL,
       c.user_id,
       COALESCE(r.created_at, NOW()),
       NOW()
FROM pending_contribution_reviews r
JOIN place_contributions c ON c.id = r.contribution_id
CROSS JOIN LATERAL jsonb_array_elements_text(
    CASE
        WHEN LEFT(BTRIM(COALESCE(r.photos, '')), 1) = '['
         AND RIGHT(BTRIM(COALESCE(r.photos, '')), 1) = ']'
            THEN BTRIM(r.photos)::jsonb
        ELSE '[]'::jsonb
    END
)
    WITH ORDINALITY AS photo(url, position)
WHERE photo.url IS NOT NULL
  AND btrim(photo.url) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM media_assets m
      WHERE m.entity_type = 'PENDING_REVIEW'
        AND m.entity_id = r.id
        AND m.url = btrim(photo.url)
  );

-- The old trip_photos table is an earlier memory implementation. Migrate its
-- primary photo URL when it is not already represented by a current memory row.
INSERT INTO media_assets (
    id, trip_id, activity_id, entity_type, entity_id, media_type, url,
    asset_role, taken_at, latitude, longitude, uploaded_by, created_at, updated_at
)
SELECT tp.id,
       tp.trip_id,
       tp.activity_id,
       CASE WHEN tp.activity_id IS NULL THEN 'TRIP_MEMORY' ELSE 'TRIP_ACTIVITY_MEMORY' END,
       CASE WHEN tp.activity_id IS NULL THEN tp.trip_id ELSE tp.activity_id END,
       'IMAGE',
       tp.photo_url,
       'MEMORY',
       tp.taken_at,
       tp.lat,
       tp.lng,
       tp.uploaded_by,
       COALESCE(tp.created_at, NOW()),
       NOW()
FROM trip_photos tp
WHERE tp.photo_url IS NOT NULL
  AND btrim(tp.photo_url) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM media_assets m
      WHERE m.entity_type IN ('TRIP_MEMORY', 'TRIP_ACTIVITY_MEMORY')
        AND m.trip_id = tp.trip_id
        AND m.url = tp.photo_url
  )
  AND NOT EXISTS (SELECT 1 FROM media_assets m WHERE m.id = tp.id);

-- Disable the old storage surfaces. The columns remain temporarily only so a
-- later cleanup migration can drop them after deployment verification.
UPDATE expenses SET photo_urls = NULL, receipt_url = NULL
WHERE photo_urls IS NOT NULL OR receipt_url IS NOT NULL;

UPDATE user_reviews SET photos = NULL WHERE photos IS NOT NULL;
UPDATE pending_contribution_reviews SET photos = NULL WHERE photos IS NOT NULL;
