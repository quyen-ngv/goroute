-- Social video extraction moves to OpenAI gpt-6-luna with reasoning off.
-- The reasoning model set before spent its whole token budget thinking and
-- returned empty content, so every job failed. Needs the worker build that
-- sends reasoning_effort=none; an older worker would hit the same failure.
UPDATE config
SET value = 'OPENAI', updated_at = NOW()
WHERE label = 'SOCIAL_LOCATION'
  AND key = 'AI_PROVIDER';

UPDATE config
SET value = 'gpt-6-luna', updated_at = NOW()
WHERE label = 'SOCIAL_LOCATION'
  AND key = 'AI_MODEL';
