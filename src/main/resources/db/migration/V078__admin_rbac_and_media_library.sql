CREATE TABLE IF NOT EXISTS admin_roles (
    id UUID PRIMARY KEY,
    code VARCHAR(80) UNIQUE NOT NULL,
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS admin_permissions (
    id UUID PRIMARY KEY,
    resource VARCHAR(80) NOT NULL,
    action VARCHAR(20) NOT NULL,
    UNIQUE(resource, action)
);

CREATE TABLE IF NOT EXISTS admin_role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY(role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS admin_user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY(user_id, role_id)
);

CREATE TABLE IF NOT EXISTS admin_resource_owners (
    resource VARCHAR(80) NOT NULL,
    resource_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY(resource, resource_id)
);

CREATE INDEX IF NOT EXISTS idx_admin_user_roles_user ON admin_user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_admin_role_permissions_role ON admin_role_permissions(role_id);

INSERT INTO admin_roles(id, code, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'SUPER_ADMIN', 'Super administrator')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
 ('foods','get'),('foods','create'),('foods','update'),('foods','delete'),
 ('places','get'),('places','create'),('places','update'),('places','delete'),
 ('contributions','get'),('contributions','update'),
 ('users','get'),('users','create'),('users','update'),('users','delete'),
 ('roles','get'),('roles','create'),('roles','update'),
 ('media','get'),('media','create'),('media','delete'),
 ('plans','get'),('plans','update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM admin_roles r CROSS JOIN admin_permissions p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
