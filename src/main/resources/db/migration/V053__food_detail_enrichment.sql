-- Food detail enrichment: subtitle, price range, core ingredients (backward-compatible nullable columns)

ALTER TABLE foods
    ADD COLUMN IF NOT EXISTS subtitle_vi VARCHAR(200),
    ADD COLUMN IF NOT EXISTS subtitle_en VARCHAR(200),
    ADD COLUMN IF NOT EXISTS subtitle_ja VARCHAR(200),
    ADD COLUMN IF NOT EXISTS subtitle_ko VARCHAR(200),
    ADD COLUMN IF NOT EXISTS price_min BIGINT,
    ADD COLUMN IF NOT EXISTS price_max BIGINT,
    ADD COLUMN IF NOT EXISTS price_currency VARCHAR(3) DEFAULT 'VND',
    ADD COLUMN IF NOT EXISTS core_ingredients TEXT;
