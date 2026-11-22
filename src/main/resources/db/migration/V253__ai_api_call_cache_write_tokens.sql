-- Claude bills prompt-cache writes above the input price: 1.25x for the 5-minute cache, 2x for the
-- 1-hour cache. The log kept only cache reads, so those tokens were priced as plain input (Python
-- worker) or dropped altogether (Java Claude client). Both are now kept apart, as subsets of
-- input_tokens like cached_input_tokens. AI_PRICING may give "cacheWrite5m" / "cacheWrite1h";
-- without them the cost uses 1.25x / 2x the input price.
ALTER TABLE ai_api_calls
    ADD COLUMN IF NOT EXISTS cache_write_5m_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS cache_write_1h_tokens INTEGER;

ALTER TABLE ai_api_calls
    ADD CONSTRAINT chk_ai_api_calls_cache_write CHECK (
        (cache_write_5m_tokens IS NULL OR cache_write_5m_tokens >= 0)
        AND (cache_write_1h_tokens IS NULL OR cache_write_1h_tokens >= 0)
    );

-- Recount Claude rows from the stored provider usage. A response payload is one Messages answer
-- (Java client, Python chat) or a list of them (Python web search, resumed after pause_turn).
-- input_tokens becomes uncached + cache read + cache write for every row; cost_usd is cleared so
-- the admin stats price the row again with today's AI_PRICING.
WITH usage_rows AS (
    SELECT c.id, m -> 'usage' AS usage
    FROM ai_api_calls c,
         LATERAL jsonb_array_elements(
             CASE WHEN jsonb_typeof(c.response_payload) = 'array' THEN c.response_payload
                  ELSE jsonb_build_array(c.response_payload) END) AS m
    WHERE c.provider = 'anthropic'
      AND c.response_payload IS NOT NULL
), parts AS (
    SELECT id,
           COALESCE((usage ->> 'input_tokens')::int, 0) AS uncached,
           COALESCE((usage ->> 'cache_read_input_tokens')::int, 0) AS cache_read,
           COALESCE((usage ->> 'cache_creation_input_tokens')::int,
                    COALESCE((usage -> 'cache_creation' ->> 'ephemeral_5m_input_tokens')::int, 0)
                        + COALESCE((usage -> 'cache_creation' ->> 'ephemeral_1h_input_tokens')::int, 0)) AS cache_write,
           COALESCE((usage -> 'cache_creation' ->> 'ephemeral_1h_input_tokens')::int, 0) AS cache_write_1h
    FROM usage_rows
    WHERE jsonb_typeof(usage) = 'object'
), totals AS (
    SELECT id,
           SUM(uncached) AS uncached,
           SUM(cache_read) AS cache_read,
           SUM(GREATEST(cache_write - cache_write_1h, 0)) AS cache_write_5m,
           SUM(LEAST(cache_write_1h, cache_write)) AS cache_write_1h
    FROM parts
    GROUP BY id
)
UPDATE ai_api_calls c
SET input_tokens = t.uncached + t.cache_read + t.cache_write_5m + t.cache_write_1h,
    cached_input_tokens = t.cache_read,
    cache_write_5m_tokens = t.cache_write_5m,
    cache_write_1h_tokens = t.cache_write_1h,
    total_tokens = t.uncached + t.cache_read + t.cache_write_5m + t.cache_write_1h + COALESCE(c.output_tokens, 0),
    cost_usd = NULL
FROM totals t
WHERE c.id = t.id;
