-- Other names a place is searched by ("lăng bác" for Lăng Chủ tịch Hồ Chí Minh, "hồ gươm" for
-- Hồ Hoàn Kiếm): indexed beside its title and translated names, never shown as the name.
ALTER TABLE places ADD COLUMN IF NOT EXISTS search_aliases TEXT[] NOT NULL DEFAULT '{}';
