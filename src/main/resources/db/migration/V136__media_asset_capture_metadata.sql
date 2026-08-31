-- Capture metadata on every image, and expense receipts moved in beside the
-- trip memories so all images live in one table.
--
-- `taken_at` is when the shutter fired; `created_at` stays what it always was,
-- the moment the row was written. Timelines read `taken_at`, auditing reads
-- `created_at`, and neither has to lie for the other.
ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS taken_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7),
    -- How the file reached us: the in-app camera, or the system picker.
    ADD COLUMN IF NOT EXISTS capture_source VARCHAR(20),
    -- Where `taken_at` came from, so the app knows whether to trust it or ask.
    -- EXIF > FILE (filesystem mtime) > CAPTURE (shutter, in-app) > UPLOAD
    -- (nothing better was available) > MANUAL (the author typed it).
    ADD COLUMN IF NOT EXISTS date_source VARCHAR(20);

-- Timeline queries sort by when the photo was taken, falling back to when it
-- arrived for the rows that predate this column.
CREATE INDEX IF NOT EXISTS idx_media_assets_trip_taken
    ON media_assets (trip_id, COALESCE(taken_at, created_at) DESC)
    WHERE deleted_at IS NULL;

-- Existing expense receipts become media_assets rows.
--
-- `expenses.photo_urls` is deliberately left in place and still populated: it
-- is what the current response DTO is built from, and a client that has not
-- been updated keeps reading it. This backfill only adds the richer rows
-- beside it. It is idempotent, so a re-run cannot duplicate anything.
INSERT INTO media_assets (
    id, trip_id, activity_id, entity_type, entity_id,
    media_type, url, uploaded_by, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    e.trip_id,
    e.activity_id,
    'EXPENSE',
    e.id,
    'IMAGE',
    photo.url,
    COALESCE(e.created_by, e.paid_by),
    COALESCE(e.created_at, NOW()),
    NOW()
FROM expenses e
CROSS JOIN LATERAL unnest(e.photo_urls) AS photo(url)
WHERE e.photo_urls IS NOT NULL
  AND photo.url IS NOT NULL
  AND btrim(photo.url) <> ''
  AND COALESCE(e.created_by, e.paid_by) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM media_assets m
      WHERE m.entity_type = 'EXPENSE'
        AND m.entity_id = e.id
        AND m.url = photo.url
  );
