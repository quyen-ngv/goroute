ALTER TABLE location_images
    ADD COLUMN IF NOT EXISTS slogan VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description JSONB NOT NULL DEFAULT '[
      {"type":"DESCRIPTION","content":{"title":"","content":null}},
      {"type":"VIBE","content":{"title":"Vibe ở đây thế nào","content":null}},
      {"type":"ACCOMODATION","content":{"title":"Ngủ lại ở đâu","content":null}},
      {"type":"CUISINE","content":{"title":"Đặc sản tại đây","content":null}},
      {"type":"SEASON","content":{"title":"Nên đi mùa nào","content":null}},
      {"type":"NOTES","content":{"title":"Lưu ý thêm","content":null}}
    ]'::jsonb;
