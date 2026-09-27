-- Quest platform, foundation: admin permissions (§6.2), following the V167 pattern.
--
-- No `quests:publish`: §3.1 folds review and publish into one action — the review decision
-- *is* the publish — so a separate publish permission would only gate a button nobody could
-- press. `quests:location-data` is the right to read raw location samples (§3.7, §7.6); it is
-- deliberately narrower than the rest.

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('quests', 'get'), ('quests', 'review'), ('quests', 'update'),
    ('quests', 'location-data'),
    ('quest-creators', 'get'), ('quest-creators', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

-- Everything except the raw-location right goes to the three operating roles.
INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p
  ON (p.resource = 'quests' AND p.action IN ('get', 'review', 'update'))
  OR (p.resource = 'quest-creators' AND p.action IN ('get', 'update'))
WHERE r.code IN ('SUPER_ADMIN', 'MODERATOR', 'OPERATIONS')
ON CONFLICT DO NOTHING;

-- Reading raw location samples is a SUPER_ADMIN-only right; without this line the boundary
-- §3.7 and §7.6 rely on is not enforceable.
INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'quests' AND p.action = 'location-data'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
