-- Camera looks: photo filters and photo frames of the check-in camera, managed in the console.
--
-- The app bundles a default set; rows here are added on top of it, and a row whose key equals a
-- bundled one replaces it in the app. A look is baked into the photo's pixels on the phone, so
-- nothing here is needed to display a post -- the keys kept on a photo are only a record of what
-- it was taken with (usage counts, and what refuses a delete).
--
-- LUT and frame PNGs live under the storage prefix camera-looks/, which image compression never
-- touches: a re-encoded LUT gives wrong colours. No foreign keys, as in the rest of the schema.

CREATE TABLE IF NOT EXISTS camera_looks (
    id            UUID         PRIMARY KEY,
    kind          VARCHAR(16)  NOT NULL CHECK (kind IN ('FILTER', 'FRAME')),
    -- Stable code inside the kind, lowercase snake_case, e.g. 'old_town'. Photos reference it.
    look_key      VARCHAR(80)  NOT NULL CHECK (look_key ~ '^[a-z0-9]+(_[a-z0-9]+)*$'),
    -- Language tag -> name; 'en' always present.
    names         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- FILTER: adjust / matrix / lut / strength / effects. FRAME: images / photoRect / texts.
    definition    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    thumbnail_url TEXT,
    sort_order    INT          NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    -- Seasonal looks: when set, the look is served only inside the window (UTC).
    starts_at     TIMESTAMP,
    ends_at       TIMESTAMP,
    source        TEXT,
    license_note  TEXT,
    -- Bumped on every update.
    version       INT          NOT NULL DEFAULT 1,
    created_by    UUID,
    updated_by    UUID,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_camera_looks_kind_key UNIQUE (kind, look_key)
);

-- The console list and the public manifest: one kind in display order.
CREATE INDEX IF NOT EXISTS idx_camera_looks_kind_order ON camera_looks (kind, sort_order, id);

-- Which look a check-in photo was taken with. Check-in photos are media_assets rows
-- (entity_type = 'USER_CHECKIN'); both columns stay NULL for every other row and for old apps.
SET LOCAL lock_timeout = '10s';
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS filter_key VARCHAR(80);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS frame_key VARCHAR(80);

-- Usage counts in the console list, and the "still used" check before a delete.
CREATE INDEX IF NOT EXISTS idx_media_assets_filter_key ON media_assets (filter_key) WHERE filter_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_media_assets_frame_key ON media_assets (frame_key) WHERE frame_key IS NOT NULL;

INSERT INTO config (label, key, value, description, is_active)
VALUES ('CHECKIN', 'CAMERA_LOOKS_ENABLED', 'false',
        'Cho moi nguoi dung filter va khung anh trong camera check-in (user beta luon duoc). true/false', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- Permissions. SUPER_ADMIN is granted explicitly: nothing grants new permissions to it automatically.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('camera-looks', 'get'), ('camera-looks', 'create'), ('camera-looks', 'update'), ('camera-looks', 'delete')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'camera-looks' AND p.action IN ('get', 'create', 'update', 'delete')
WHERE r.code IN ('SUPER_ADMIN', 'CONTENT_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'camera-looks' AND p.action = 'get'
WHERE r.code = 'OPERATIONS'
ON CONFLICT DO NOTHING;
