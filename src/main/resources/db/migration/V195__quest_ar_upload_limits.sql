-- AR objects (§3.15): the upload limits, as rows an admin can change on the console's config page
-- (label QUEST). The code falls back to the same defaults when a row is missing.
--   AR_MAX_MODEL_MB         largest GLB or USDZ, in MB (players download it with the quest)
--   AR_CREATOR_MAX_OBJECTS  how many 3D objects one creator may upload
-- AR_MAX_MODEL_MB is also public, so the app refuses an oversized file before uploading it.
-- ON CONFLICT DO NOTHING: values an admin already set are never overwritten.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('QUEST', 'AR_MAX_MODEL_MB', '50',
     'Dung luong toi da (MB) cua file GLB/USDZ cho vat the AR; nguoi choi tai file nay cung quest', TRUE),
    ('QUEST', 'AR_CREATOR_MAX_OBJECTS', '20',
     'So vat the AR toi da moi creator duoc tu tai len', TRUE)
ON CONFLICT (label, key) DO NOTHING;

UPDATE config
SET value = value || ', {label=''QUEST'',key=''AR_MAX_MODEL_MB''}',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'CONFIG' AND key = 'PUBLIC_CONFIG'
  AND value NOT LIKE '%AR_MAX_MODEL_MB%';
