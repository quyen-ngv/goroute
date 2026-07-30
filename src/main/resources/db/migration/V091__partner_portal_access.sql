ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_account_status;
ALTER TABLE users ADD CONSTRAINT chk_users_account_status
    CHECK (account_status IN ('ACTIVE', 'LOCKED', 'DISABLED'));

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('partner-organizations','get'),('partner-organizations','create'),('partner-organizations','update'),('partner-organizations','delete'),
    ('marketplace-hotels','get'),('marketplace-hotels','create'),('marketplace-hotels','update'),('marketplace-hotels','delete'),
    ('marketplace-activities','get'),('marketplace-activities','create'),('marketplace-activities','update'),('marketplace-activities','delete'),
    ('marketplace-conversations','get'),('marketplace-conversations','create'),('marketplace-conversations','update'),('marketplace-conversations','delete'),
    ('marketplace-reviews','get'),('marketplace-reviews','update'),('marketplace-reviews','delete'),
    ('configs','get'),('configs','create'),('configs','update'),('configs','delete')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
CROSS JOIN admin_permissions p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

