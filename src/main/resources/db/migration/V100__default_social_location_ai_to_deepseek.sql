UPDATE config
SET value = 'DEEPSEEK', updated_at = NOW()
WHERE label = 'SOCIAL_LOCATION'
  AND key = 'AI_PROVIDER'
  AND (value IS NULL OR TRIM(value) = '' OR UPPER(TRIM(value)) = 'ANTHROPIC');

UPDATE config
SET value = '', updated_at = NOW()
WHERE label = 'SOCIAL_LOCATION'
  AND key = 'AI_MODEL'
  AND LOWER(TRIM(value)) LIKE 'claude%';

UPDATE config
SET value = '', updated_at = NOW()
WHERE label = 'SOCIAL_LOCATION'
  AND key = 'AI_BASE_URL'
  AND LOWER(TRIM(value)) LIKE '%anthropic.com%';
