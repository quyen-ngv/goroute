ALTER TABLE place_import_job_items
    ADD COLUMN IF NOT EXISTS source_address TEXT,
    ADD COLUMN IF NOT EXISTS source_original_url TEXT;

UPDATE place_import_job_items i
SET source_address = a.address,
    source_original_url = CASE
        WHEN a.place_id ILIKE 'http%' THEN a.place_id
        ELSE NULL
    END
FROM activities a
WHERE i.activity_id = a.id
  AND (i.source_address IS NULL OR i.source_original_url IS NULL);
