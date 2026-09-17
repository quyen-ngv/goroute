-- Lets admins list individual check-ins and hide/show them, independent of the
-- moderation report queue (which only surfaces content someone already flagged).

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('checkins', 'get'),
    ('checkins', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'checkins'
WHERE r.code IN ('SUPER_ADMIN', 'MODERATOR', 'OPERATIONS')
ON CONFLICT DO NOTHING;
