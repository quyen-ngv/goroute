-- ai_api_calls becomes the log of every AI provider call, not only the social-video job summary.
--
-- trace_id groups the calls of one job (AI trip job id, social job id); correlation_id keeps its
-- meaning as the idempotency key of one row. Cost is stored when the model has a price in
-- config label AI_PRICING at insert time; the admin page estimates it later for rows without one.
ALTER TABLE ai_api_calls
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cached_input_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS web_search_calls INTEGER,
    ADD COLUMN IF NOT EXISTS cost_usd NUMERIC(14, 6);

ALTER TABLE ai_api_calls
    ADD CONSTRAINT chk_ai_api_calls_usage_extra CHECK (
        (cached_input_tokens IS NULL OR cached_input_tokens >= 0)
        AND (web_search_calls IS NULL OR web_search_calls >= 0)
        AND (cost_usd IS NULL OR cost_usd >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_ai_api_calls_created ON ai_api_calls(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_api_calls_trace ON ai_api_calls(trace_id) WHERE trace_id IS NOT NULL;

-- Price per model, USD. Key = model id exactly as the provider reports it (lower-case).
-- Value JSON: {"input": per 1M input tokens, "cachedInput": per 1M cached input tokens,
--              "output": per 1M output tokens, "webSearchPer1k": per 1,000 web search calls}
-- No rows are seeded: prices change and must be entered from the provider's price page.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('AI_PRICING', 'example-model',
     '{"input":0,"cachedInput":0,"output":0,"webSearchPer1k":0}',
     'Mau gia AI (USD). Tao mot dong cho moi model, key = ten model chu thuong. Dong mau nay bi bo qua.',
     FALSE)
ON CONFLICT (label, key) DO NOTHING;
