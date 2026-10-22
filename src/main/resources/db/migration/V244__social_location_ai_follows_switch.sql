-- Social video AI follows the worker's AI_PROVIDER switch, the one setting that now picks the
-- provider for every AI feature (main model for the extraction, cheap model for the pre-filter).
-- An admin can still pin a provider or model here; blank means "follow the switch".
UPDATE config
SET value = '',
    description = CASE key
        WHEN 'AI_PROVIDER' THEN 'Optional pin: ANTHROPIC, DEEPSEEK, OPENAI, or OPENAI_COMPATIBLE; blank follows the worker AI_PROVIDER switch'
        WHEN 'AI_MODEL' THEN 'Optional model pin; blank uses the provider''s model from the worker settings'
        ELSE description
    END,
    updated_at = NOW()
WHERE label = 'SOCIAL_LOCATION'
  AND key IN ('AI_PROVIDER', 'AI_MODEL', 'AI_BASE_URL');
