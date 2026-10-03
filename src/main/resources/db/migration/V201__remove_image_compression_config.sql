-- V201: Remove the image compression toggle
-- Epic: Media upload
-- Purpose: uploads no longer call the ImagePress service, so MEDIA_UPLOAD.COMPRESSION_ENABLED
-- (seeded by V159) controls nothing. Drop the row so the admin console stops offering it.

DELETE FROM config
WHERE label = 'MEDIA_UPLOAD'
  AND key = 'COMPRESSION_ENABLED';
