-- Console permissions for managed accounts and posts (epic 20), same pattern as V210/V273.
--
-- SUPER_ADMIN and CONTENT_MANAGER get all of them. MODERATOR gets posts:get and posts:update,
-- which is what hiding and showing a post needs (/admin/posts/{id}/hide, /show). Writing for a
-- managed account also checks posts:update, so a moderator could edit a managed account's post;
-- that is the plan's choice, not an oversight.

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('managed-accounts', 'get'),
    ('managed-accounts', 'create'),
    ('managed-accounts', 'update'),
    ('posts', 'get'),
    ('posts', 'create'),
    ('posts', 'update'),
    ('posts', 'delete')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p
  ON (p.resource = 'managed-accounts' AND p.action IN ('get', 'create', 'update'))
  OR (p.resource = 'posts' AND p.action IN ('get', 'create', 'update', 'delete'))
WHERE r.code IN ('SUPER_ADMIN', 'CONTENT_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'posts' AND p.action IN ('get', 'update')
WHERE r.code = 'MODERATOR'
ON CONFLICT DO NOTHING;
