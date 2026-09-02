-- Admin announcements (console "Thông báo" screen).
--
-- `get` covers resolving an audience, searching recipients and reading what was already sent;
-- `create` is the send itself. They are separate so an operator can be trusted to inspect the
-- reach of a segment without also being able to push to it.

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('notifications', 'get'),
    ('notifications', 'create')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'notifications'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
