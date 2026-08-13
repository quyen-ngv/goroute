-- Destructive image migrations and storage cleanup must never be available to a
-- generic admin role.  They are reserved for explicitly authorised operators.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('activity-bookings', 'create'),
    ('activity-bookings', 'update'),
    ('activity-bookings', 'delete'),
    ('location-images', 'create'),
    ('location-images', 'update'),
    ('location-images', 'delete'),
    ('dashboard', 'get'),
    ('system-maintenance', 'get'),
    ('system-maintenance', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource IN ('activity-bookings', 'location-images', 'dashboard', 'system-maintenance')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
