-- Lets admins list public trips and hide/show or unpublish one directly, the trip
-- counterpart of the check-in console (V167).

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('public-trips', 'get'),
    ('public-trips', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'public-trips'
WHERE r.code IN ('SUPER_ADMIN', 'MODERATOR', 'OPERATIONS')
ON CONFLICT DO NOTHING;
