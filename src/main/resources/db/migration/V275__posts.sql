-- Posts (blog articles), epic 20 (.docs/BLOG_POSTS_PLAN_2026-11-10.md §2).
--
-- A post is separate from a check-in: a title, a cover, a markdown body with photos and videos
-- between the paragraphs, any number of catalogue places, and optionally the trip it is about.
-- Its media are ordinary media_assets rows (entity_type = 'POST', asset_role COVER or BODY), so
-- the storage sweep reads them the way it reads every other upload.
--
-- No physical foreign keys, as everywhere else: user_id, trip_id, cover_media_id and place_id are
-- logical references checked by the service.

CREATE TABLE IF NOT EXISTS posts (
    id             UUID PRIMARY KEY,
    -- The author (users.id). For a managed account this is the account, not the admin.
    user_id        UUID         NOT NULL,
    post_type      VARCHAR(20)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    -- Empty: the server cuts one from the body on every save.
    excerpt        VARCHAR(500),
    -- CommonMark. Images and videos only as ![caption](media:<uuid>) of this post's own media.
    body           TEXT         NOT NULL DEFAULT '',
    -- media_assets.id of a COVER or BODY row of this post.
    cover_media_id UUID,
    -- TRIP_STORY only.
    trip_id        UUID,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    visibility     VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    -- Set by the first publish and kept by an unpublish: the follower fan-out runs only when this
    -- goes from NULL to a time.
    published_at   TIMESTAMP,
    like_count     INT          NOT NULL DEFAULT 0,
    read_minutes   INT          NOT NULL DEFAULT 1,
    is_removed     BOOLEAN      NOT NULL DEFAULT FALSE,
    edited_at      TIMESTAMP,
    -- Who pressed the button: the author, or the admin writing for a managed account.
    created_by     UUID         NOT NULL,
    updated_by     UUID         NOT NULL,
    version        INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_posts_type CHECK (post_type IN ('ARTICLE', 'TRIP_STORY')),
    CONSTRAINT chk_posts_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT chk_posts_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_posts_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

-- The feed: published + public + not removed, newest first.
CREATE INDEX IF NOT EXISTS idx_posts_feed ON posts (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC' AND is_removed = FALSE;
-- A profile's posts, and "does this user already have a story of this trip".
CREATE INDEX IF NOT EXISTS idx_posts_user ON posts (user_id, updated_at DESC) WHERE is_removed = FALSE;

CREATE TABLE IF NOT EXISTS post_places (
    post_id  UUID NOT NULL,
    place_id UUID NOT NULL,
    position INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, place_id)
);
-- Place Detail's "posts about this place", and the near-you radius joined back from places.
CREATE INDEX IF NOT EXISTS idx_post_places_place ON post_places (place_id, post_id);

CREATE TABLE IF NOT EXISTS post_likes (
    post_id    UUID      NOT NULL,
    user_id    UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

-- ---------------------------------------------------------------------------------------
-- The shared social tables address content by type. Three of them still enumerate the types in
-- a CHECK; each is replaced with the same list plus POST. The constraint is found by what it
-- checks rather than by name, because an inline column CHECK is named by PostgreSQL.

DO $$
DECLARE
    found RECORD;
BEGIN
    FOR found IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'saved_posts'::regclass
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%post_type%'
    LOOP
        EXECUTE format('ALTER TABLE saved_posts DROP CONSTRAINT %I', found.conname);
    END LOOP;

    FOR found IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'content_comments'::regclass
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%content_type%'
    LOOP
        EXECUTE format('ALTER TABLE content_comments DROP CONSTRAINT %I', found.conname);
    END LOOP;

    FOR found IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'follower_fanout_logs'::regclass
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%post_type%'
    LOOP
        EXECUTE format('ALTER TABLE follower_fanout_logs DROP CONSTRAINT %I', found.conname);
    END LOOP;
END $$;

ALTER TABLE saved_posts
    ADD CONSTRAINT saved_posts_post_type_check
        CHECK (post_type IN ('CHECKIN', 'REVIEW', 'POST'));

ALTER TABLE content_comments
    ADD CONSTRAINT content_comments_content_type_check
        CHECK (content_type IN ('TRIP', 'CHECKIN', 'REVIEW', 'PLACE_COLLECTION', 'POST'));

ALTER TABLE follower_fanout_logs
    ADD CONSTRAINT follower_fanout_logs_post_type_check
        CHECK (post_type IN ('CHECKIN', 'TRIP', 'POST'));

-- content_reports, content_takedowns and moderation_flags have no type CHECK (V117): nothing to do.

-- Runtime switches and limits (BusinessConfigKey POST_*). ON CONFLICT DO NOTHING: values an admin
-- already set are kept.
INSERT INTO config (label, key, value, description, is_active)
VALUES ('POST', 'ENABLED', 'false',
        'Bai viet hien tren Feed, Place Detail, ho so; tat thi chi USER.BETA_USER thay. GET /posts/{id} luon doc duoc', TRUE),
       ('POST', 'USER_WRITE_ENABLED', 'false',
        'User tao/sua bai viet trong app; tat thi chi USER.BETA_USER viet duoc (admin console khong bi chan)', TRUE),
       ('POST', 'MAX_BODY_LENGTH', '30000',
        'Do dai toi da (ky tu) cua noi dung bai viet (1000-100000)', TRUE),
       ('POST', 'MAX_MEDIA', '40',
        'So anh/video toi da mot bai viet (1-200)', TRUE),
       ('POST', 'MAX_VIDEO_SECONDS', '300',
        'Do dai toi da (giay) cua mot video trong bai viet (5-900)', TRUE),
       ('POST', 'MAX_PLACES', '30',
        'So dia diem toi da gan vao mot bai viet (1-100)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
