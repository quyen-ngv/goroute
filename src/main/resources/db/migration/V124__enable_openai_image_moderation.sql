-- OpenAI moderation is free and the provider is configured through deployment secrets.
-- V118 created this row with a safe disabled default before any provider was available.
UPDATE config
SET value = 'true',
    description = 'Bat kiem duyet anh OpenAI cho nhom sexual va violence'
WHERE label = 'MODERATION'
  AND key = 'IMAGE_MODERATION_ENABLED';
