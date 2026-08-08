INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('SOCIAL_LOCATION', 'AI_PROVIDER', 'DEEPSEEK', 'AI provider: ANTHROPIC, DEEPSEEK, OPENAI, or OPENAI_COMPATIBLE', TRUE),
    ('SOCIAL_LOCATION', 'AI_MODEL', '', 'Optional model override; blank uses the worker default for the selected provider', TRUE),
    ('SOCIAL_LOCATION', 'AI_BASE_URL', '', 'Optional AI API base URL override; API keys remain in worker secrets', TRUE)
ON CONFLICT (label, key) DO NOTHING;
