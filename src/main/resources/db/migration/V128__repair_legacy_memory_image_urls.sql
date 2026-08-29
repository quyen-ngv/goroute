-- An older mobile build persisted ImageUploadOutcome.toString() rather than
-- its URL. Restore those memory records before GET trip serves them to clients.
UPDATE media_assets
SET url = substring(url FROM 'url: (https?://[^,[:space:]}]+)')
WHERE url LIKE '{originalFilename:%'
  AND url ~ 'url: https?://[^,[:space:]}]+';
