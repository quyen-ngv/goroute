-- A check-in can carry one video instead of its photos (8/11/2026). The video row keeps the file
-- in url and the still frame the app cut from it in thumbnail_url, which every surface that can
-- only draw an image (feed card, review, admin, cluster samples) shows instead.
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(1000);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;

-- Runtime knobs. ON CONFLICT DO NOTHING: values an admin already set are kept.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('CHECKIN', 'VIDEO_ENABLED', 'false', 'Cho moi nguoi dang video trong check-in (beta luon duoc bat)', TRUE),
    ('CHECKIN', 'MAX_VIDEO_SECONDS', '60', 'Do dai toi da (giay) cua video check-in', TRUE)
ON CONFLICT (label, key) DO NOTHING;
