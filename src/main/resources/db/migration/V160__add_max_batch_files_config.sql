-- V160: Add max batch files upload configuration
-- Epic: Media upload
-- Purpose: Allow admin to configure maximum number of files in batch upload

INSERT INTO config (label, key, value, description, is_active)
VALUES ('MEDIA_UPLOAD', 'MAX_BATCH_FILES', '30',
        'Maximum number of files allowed in a single batch upload request.',
        TRUE)
ON CONFLICT (label, key) DO NOTHING;
