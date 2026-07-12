-- place_id is the canonical activity -> places.id link in the existing model.
-- Keep the previously-added place_ref_id column for backward compatibility,
-- but make old approved mappings visible through the canonical column.
UPDATE activities
SET place_id = place_ref_id::text
WHERE NULLIF(BTRIM(place_id), '') IS NULL
  AND place_ref_id IS NOT NULL;
