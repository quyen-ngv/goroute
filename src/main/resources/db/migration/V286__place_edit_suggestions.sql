-- Place edit suggestions (epic 09, .docs/PLACE_EDIT_SUGGESTIONS_PLAN_2026-11-15.md §3.1).
-- A signed-in user suggests corrections to a catalogue place; nothing reaches `places` until an
-- operator approves it in the console. Approved fields are locked against the Google re-scrape.

CREATE TABLE IF NOT EXISTS place_edit_suggestions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id        UUID NOT NULL,
    user_id         UUID NOT NULL,
    type            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- The submitter's own words ("open 7-22, closed on Monday, has parking"); may be all there is.
    description     VARCHAR(1000),
    evidence_photos JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- Language the text was written in; a NAME / DESCRIPTION change is written to this locale.
    locale          VARCHAR(10) NOT NULL,
    reject_reason   VARCHAR(30),
    review_note     VARCHAR(500),
    review_channel  VARCHAR(20),
    reviewed_by     UUID,
    reviewed_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_pes_type CHECK (type IN ('EDIT_INFO', 'STATUS_REPORT', 'ADD_PHOTOS')),
    CONSTRAINT chk_pes_status CHECK (status IN (
        'PENDING', 'APPROVED', 'PARTIALLY_APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_pes_status_created ON place_edit_suggestions (status, created_at);
CREATE INDEX IF NOT EXISTS idx_pes_user_created ON place_edit_suggestions (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pes_place_status ON place_edit_suggestions (place_id, status);

CREATE TABLE IF NOT EXISTS place_edit_changes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suggestion_id UUID NOT NULL,
    place_id      UUID NOT NULL,
    -- Who is credited: the submitter, also for a change an operator added from their words.
    user_id       UUID NOT NULL,
    field         VARCHAR(30) NOT NULL,
    old_value     JSONB,
    new_value     JSONB,
    applied_value JSONB,
    author        VARCHAR(10) NOT NULL DEFAULT 'USER',
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(30),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_pec_author CHECK (author IN ('USER', 'ADMIN')),
    CONSTRAINT chk_pec_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED', 'CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_pec_suggestion ON place_edit_changes (suggestion_id);
CREATE INDEX IF NOT EXISTS idx_pec_place_field_pending
    ON place_edit_changes (place_id, field) WHERE status = 'PENDING';
-- One pending value per person, place and field: a second suggestion moves the row instead.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pec_user_place_field_pending
    ON place_edit_changes (user_id, place_id, field) WHERE status = 'PENDING' AND author = 'USER';

-- PlaceEditField names the re-scrape must not overwrite (PlaceServiceImpl.updateExistingPlace).
-- Outside the generic place update on purpose: jobs re-save whole Place rows.
ALTER TABLE places ADD COLUMN IF NOT EXISTS locked_fields VARCHAR(30)[] NOT NULL DEFAULT '{}';

-- Business config (BusinessConfigKey PLACE_EDIT_*). The flag ships off; beta users always pass.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('PLACE_EDIT', 'ENABLED', 'false',
     'Bat de xuat chinh sua dia diem cho moi nguoi dung; beta user luon duoc dung. true/false', TRUE),
    ('PLACE_EDIT', 'MIN_ACCOUNT_AGE_HOURS', '24',
     'Tai khoan phai tao it nhat bao nhieu gio moi duoc de xuat (0..720)', TRUE),
    ('PLACE_EDIT', 'MAX_SUBMISSIONS_PER_DAY', '10',
     'So de xuat toi da cua mot nguoi trong 24 gio (1..100)', TRUE),
    ('PLACE_EDIT', 'MAX_PENDING_PER_USER', '30',
     'So de xuat dang cho duyet toi da cua mot nguoi (1..500)', TRUE),
    ('PLACE_EDIT', 'MAX_PIN_MOVE_METERS', '2000',
     'Khoang cach toi da (met) duoc doi vi tri ghim (10..50000)', TRUE),
    ('PLACE_EDIT', 'REWARD_STARS', '1',
     'So Star thuong moi de xuat duoc duyet (0..100)', TRUE),
    ('PLACE_EDIT', 'MAX_REWARDS_PER_DAY', '5',
     'So lan thuong de xuat toi da cua mot nguoi trong 24 gio (0..100)', TRUE),
    ('PLACE_EDIT', 'WEBSITE_BLOCKED_HOSTS', 'bit.ly,tinyurl.com,t.co,goo.gl',
     'Ten mien website khong nhan, cach nhau boi dau phay', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- Console permissions. SUPER_ADMIN has no automatic grant, so it is granted explicitly.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES ('place-edits', 'get'), ('place-edits', 'update')) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'place-edits' AND p.action IN ('get', 'update')
WHERE r.code IN ('SUPER_ADMIN', 'MODERATOR', 'OPERATIONS')
ON CONFLICT DO NOTHING;
