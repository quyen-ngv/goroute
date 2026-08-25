ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS media_type VARCHAR(16) NOT NULL DEFAULT 'IMAGE';

ALTER TABLE media_assets
    DROP CONSTRAINT IF EXISTS chk_media_assets_media_type;

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_media_type
        CHECK (media_type IN ('IMAGE', 'VIDEO'));
