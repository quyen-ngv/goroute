ALTER TABLE city_stories ALTER COLUMN image_url DROP NOT NULL;
ALTER TABLE city_stories ADD COLUMN media_type VARCHAR(10) NOT NULL DEFAULT 'IMAGE';
ALTER TABLE city_stories ADD COLUMN video_url TEXT;
ALTER TABLE city_stories ADD COLUMN thumbnail_url TEXT;
ALTER TABLE city_stories ADD CONSTRAINT city_stories_media_type_check CHECK (media_type IN ('IMAGE', 'VIDEO'));
ALTER TABLE city_stories ADD CONSTRAINT city_stories_media_url_check CHECK (
    (media_type = 'IMAGE' AND image_url IS NOT NULL) OR
    (media_type = 'VIDEO' AND video_url IS NOT NULL)
);
