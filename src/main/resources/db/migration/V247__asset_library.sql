-- Shared image library (epic 06 §2.5b): curated images that other records reach by asset id
-- instead of copying a URL. Replacing an asset's image changes it everywhere it is used.
--
-- First consumer: food ingredients (foods.core_ingredients[].assetId). Banners and
-- promotions get their own collections here; how they are placed is a separate feature.
--
-- Additive only. No foreign key from consumers: references live inside JSON columns, and
-- an asset that is still in use cannot be deleted (checked by the service).
CREATE TABLE IF NOT EXISTS asset_library (
    id           UUID         PRIMARY KEY,
    collection   VARCHAR(32)  NOT NULL CHECK (collection IN ('INGREDIENT', 'BANNER', 'PROMOTION', 'GENERAL')),
    -- Stable code inside the collection, lowercase kebab-case, e.g. 'lemongrass'.
    asset_key    VARCHAR(80)  NOT NULL CHECK (asset_key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name_vi      VARCHAR(200),
    name_en      VARCHAR(200) NOT NULL,
    name_ja      VARCHAR(200),
    name_ko      VARCHAR(200),
    description  TEXT,
    tags         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- NULL while the asset is only a placeholder waiting for an image ("icons still to find").
    image_url    TEXT,
    source       VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN'
                 CHECK (source IN ('ICONSCOUT', 'FLUENT_EMOJI', 'AI_GENERATED', 'IN_HOUSE', 'PARTNER', 'UNKNOWN')),
    source_url   TEXT,
    license_note TEXT,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    -- Bumped on every image replacement; lets the console show "image v3".
    version      INT          NOT NULL DEFAULT 0,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asset_library_collection_key UNIQUE (collection, asset_key)
);

-- The console grid: one collection, newest first, optionally only live assets.
CREATE INDEX IF NOT EXISTS idx_asset_library_collection_status
    ON asset_library (collection, status, updated_at DESC);

-- Permissions. SUPER_ADMIN is granted explicitly: nothing grants new permissions to it automatically.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('assets', 'get'), ('assets', 'create'), ('assets', 'update'), ('assets', 'delete')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'assets' AND p.action IN ('get', 'create', 'update', 'delete')
WHERE r.code IN ('SUPER_ADMIN', 'CONTENT_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'assets' AND p.action = 'get'
WHERE r.code IN ('OPERATIONS', 'MARKETPLACE_MANAGER')
ON CONFLICT DO NOTHING;
