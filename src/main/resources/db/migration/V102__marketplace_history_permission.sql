INSERT INTO admin_permissions(id, resource, action)
VALUES (md5('marketplace-history:get')::uuid, 'marketplace-history', 'get')
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p
  ON p.resource = 'marketplace-history' AND p.action = 'get'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
