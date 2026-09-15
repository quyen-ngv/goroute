-- V159: Add image compression toggle configuration
-- Epic: Media upload
-- Purpose: Allow admin to enable/disable external ImagePress compression service

INSERT INTO config (label, key, value, description, is_active)
VALUES ('MEDIA_UPLOAD', 'COMPRESSION_ENABLED', 'false',
        'Enable external ImagePress compression service. When disabled, images are stored as-is after validation and moderation.',
        TRUE)
ON CONFLICT (label, key) DO NOTHING;
