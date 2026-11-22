-- Quest management on the console: a list of every quest, operational status changes and
-- delete. Status changes ride on `quests:review` (pausing or suspending a live quest is the same
-- moderation call as publishing it). Delete is new and destructive, so it gets its own right and
-- only SUPER_ADMIN holds it by default; Role management can hand it to another role.

INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES ('quests', 'delete')) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'quests' AND p.action = 'delete'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- The management list reads live rows across all creators, newest first, optionally by status.
CREATE INDEX IF NOT EXISTS idx_quests_status_updated
    ON quests (status, updated_at DESC)
    WHERE is_deleted = FALSE;
