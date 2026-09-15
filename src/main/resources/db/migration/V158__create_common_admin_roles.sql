-- Common admin roles for delegating specific responsibilities
-- These roles follow the principle of least privilege

-- ============================================
-- CONTENT_MANAGER: Manages places, foods, and user contributions
-- ============================================
INSERT INTO admin_roles(id, code, name)
VALUES (gen_random_uuid(), 'CONTENT_MANAGER', 'Content Manager')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
CROSS JOIN admin_permissions p
WHERE r.code = 'CONTENT_MANAGER'
  AND p.resource IN ('places', 'foods', 'contributions', 'location-images')
  AND p.action IN ('get', 'create', 'update')
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'users' AND p.action = 'get'
WHERE r.code = 'CONTENT_MANAGER'
ON CONFLICT DO NOTHING;

-- ============================================
-- MODERATOR: Handles content moderation queue
-- ============================================
INSERT INTO admin_roles(id, code, name)
VALUES (gen_random_uuid(), 'MODERATOR', 'Content Moderator')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
CROSS JOIN admin_permissions p
WHERE r.code = 'MODERATOR'
  AND p.resource IN ('moderation-queue', 'moderation-terms', 'moderation-metrics')
  AND p.action IN ('get', 'update')
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource IN ('users', 'places', 'contributions') AND p.action = 'get'
WHERE r.code = 'MODERATOR'
ON CONFLICT DO NOTHING;

-- ============================================
-- MARKETPLACE_MANAGER: Manages marketplace operations
-- ============================================
INSERT INTO admin_roles(id, code, name)
VALUES (gen_random_uuid(), 'MARKETPLACE_MANAGER', 'Marketplace Manager')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
CROSS JOIN admin_permissions p
WHERE r.code = 'MARKETPLACE_MANAGER'
  AND p.resource IN (
      'marketplace-hotels', 
      'marketplace-activities', 
      'marketplace-reviews',
      'marketplace-conversations',
      'marketplace-history',
      'activity-bookings'
  )
  AND p.action IN ('get', 'create', 'update')
ON CONFLICT DO NOTHING;

-- ============================================
-- SUPPORT: Customer support (read-mostly)
-- ============================================
INSERT INTO admin_roles(id, code, name)
VALUES (gen_random_uuid(), 'SUPPORT', 'Customer Support')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
CROSS JOIN admin_permissions p
WHERE r.code = 'SUPPORT'
  AND p.resource IN ('users', 'plans', 'subscriptions', 'activity-bookings', 'places', 'foods')
  AND p.action = 'get'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource IN ('users', 'subscriptions') AND p.action = 'update'
WHERE r.code = 'SUPPORT'
ON CONFLICT DO NOTHING;

-- ============================================
-- OPERATIONS: Manages check-ins, passports, points
-- ============================================
INSERT INTO admin_roles(id, code, name)
VALUES (gen_random_uuid(), 'OPERATIONS', 'Operations Manager')
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
CROSS JOIN admin_permissions p
WHERE r.code = 'OPERATIONS'
  AND p.resource IN (
      'checkin-clusters',
      'passport-catalog',
      'passport-rewards',
      'points',
      'provinces',
      'notifications'
  )
  AND p.action IN ('get', 'create', 'update')
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'users' AND p.action = 'get'
WHERE r.code = 'OPERATIONS'
ON CONFLICT DO NOTHING;
