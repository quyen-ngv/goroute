-- Removes the VietdeGuide marketplace (epic 07, V122) and community roles (TRUST-02, V120).
--
-- Written as a forward migration rather than by editing V120/V122: `validate-on-migrate` is
-- on, so changing a migration that has already run anywhere fails the next deployment with a
-- checksum error instead of doing what was intended.
--
-- Order matters: guide_bookings is referenced by payout entries and reviews, and
-- guide_services by bookings and availability, so children go first. CASCADE covers the
-- indexes and foreign keys those tables own.

DROP TABLE IF EXISTS guide_service_views CASCADE;
DROP TABLE IF EXISTS guide_reviews CASCADE;
DROP TABLE IF EXISTS guide_payout_entries CASCADE;
DROP TABLE IF EXISTS guide_bookings CASCADE;
DROP TABLE IF EXISTS guide_availability CASCADE;
DROP TABLE IF EXISTS guide_services CASCADE;
DROP TABLE IF EXISTS guide_identity_documents CASCADE;
DROP TABLE IF EXISTS guide_profiles CASCADE;

DROP TABLE IF EXISTS user_trust_roles CASCADE;

-- Configuration seeded by V123. Scoped to label = 'GUIDE' so the CHECKIN category keeps its
-- own GUIDE_SCREEN_* keys — those belong to the check-in guidance screen, not to this
-- marketplace.
DELETE FROM config WHERE label = 'GUIDE';

-- Admin permissions seeded by V121, and the SUPER_ADMIN grants that reference them.
DELETE FROM admin_role_permissions
WHERE permission_id IN (
    SELECT id FROM admin_permissions WHERE resource IN ('guides', 'trust-roles')
);
DELETE FROM admin_permissions WHERE resource IN ('guides', 'trust-roles');
