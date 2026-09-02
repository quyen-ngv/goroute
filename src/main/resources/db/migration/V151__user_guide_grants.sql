-- VietdeGuide, as the role actually is now.
--
-- V140 dropped the guide marketplace (guide_profiles, guide_services, bookings, payouts) and the
-- community trust roles. What replaces them is smaller and answers the question those tables were
-- built around the wrong way up: a guide is a verified person the operator has vouched for, who
-- may be given tours to run through the partner organisation they belong to. The marketplace
-- half already exists -- organization_members carries the GUIDE role and every permission that
-- goes with it -- so the only thing missing was a way to say "this person is a guide" about the
-- account itself.
--
-- Status rather than a boolean, and a row that survives revocation, because "was a guide until
-- March" is a different fact from "never was one", and a support conversation needs to tell them
-- apart.

CREATE TABLE IF NOT EXISTS user_guide_grants (
    user_id       UUID PRIMARY KEY,
    status        VARCHAR(20) NOT NULL,
    display_title VARCHAR(120),
    note          TEXT,
    granted_by    UUID,
    granted_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    revoked_by    UUID,
    revoked_at    TIMESTAMP,
    revoke_reason TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_guide_grant_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    -- A revoked grant must say when it was revoked, or "was a guide until when" has no answer.
    CONSTRAINT chk_user_guide_grant_revoked
        CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
);

-- The guide directory, and the badge lookups that join from a profile.
CREATE INDEX IF NOT EXISTS idx_user_guide_grants_active
    ON user_guide_grants (granted_at DESC)
    WHERE status = 'ACTIVE';

-- Console access. `get` reads who is a guide; `update` promotes and revokes.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('guides', 'get'),
    ('guides', 'update')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'guides'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
