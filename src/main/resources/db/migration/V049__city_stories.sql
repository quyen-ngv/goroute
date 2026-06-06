-- City avatar + stories (24h feed) with likes and views

ALTER TABLE location_images
    ADD COLUMN IF NOT EXISTS avatar_url TEXT;

UPDATE location_images
SET avatar_url = image_url
WHERE avatar_url IS NULL;

CREATE TABLE IF NOT EXISTS city_stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_image_id UUID NOT NULL,
    image_url TEXT NOT NULL,
    description TEXT,
    place_id UUID,
    like_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_city_stories_location_created
    ON city_stories(location_image_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_city_stories_created
    ON city_stories(created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS city_story_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (story_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_city_story_likes_story
    ON city_story_likes(story_id);

CREATE TABLE IF NOT EXISTS city_story_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL,
    user_id UUID NOT NULL,
    viewed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (story_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_city_story_views_user
    ON city_story_views(user_id, viewed_at DESC);
