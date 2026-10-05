-- Meetups (epic 18): small real-world activities a user proposes on the Home map ("Hoạt động"),
-- modelled on Nomadtable. A host writes "I want to ..." with a category, a place (a general
-- area circle or an exact spot), a day (flexible until local midnight, or an exact time) and who
-- may join. Every meetup is reviewed by an admin before anyone else sees it.
--
-- Shape (SPEC_V2 §2):
--   * `users.birth_year` is self-declared and nullable. Hosting or joining needs it and an age of
--     at least 18; it is never shown to other users.
--   * `meetups.latitude/longitude` is the true point; `display_latitude/display_longitude` is a
--     deterministic offset of it (seeded by the meetup id) that is what strangers see, so an
--     AREA circle or a private EXACT spot does not point at the host's real location.
--   * Times are TIMESTAMP in UTC (repo convention). `meetup_date` is the local Vietnamese date.
--     `visible_until` is when the meetup leaves the map; "expired"/"ended" are computed from it
--     at read time and never stored.
--   * `meetup_participants` holds everyone but the host, one row per (meetup, user); the row's
--     status moves through REQUESTED/JOINED/DECLINED/WITHDRAWN/LEFT/REMOVED.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS birth_year SMALLINT
        CONSTRAINT chk_users_birth_year CHECK (birth_year BETWEEN 1900 AND 2100);

CREATE TABLE IF NOT EXISTS meetups (
    id                 UUID PRIMARY KEY,
    host_user_id       UUID          NOT NULL REFERENCES users (id),
    -- The text after "muốn ..." ("wants to ...").
    title              VARCHAR(120)  NOT NULL,
    description        VARCHAR(500),
    category           VARCHAR(30)   NOT NULL,
    location_mode      VARCHAR(10)   NOT NULL,
    latitude           DECIMAL(10, 7) NOT NULL,
    longitude          DECIMAL(10, 7) NOT NULL,
    display_latitude   DECIMAL(10, 7) NOT NULL,
    display_longitude  DECIMAL(10, 7) NOT NULL,
    -- AREA only: 300, 1000 or 3000 metres.
    radius_m           INT,
    place_id           UUID,
    place_name         VARCHAR(200),
    address            VARCHAR(500),
    -- "Phường X, Tỉnh Y" from the ward that covers the true point.
    area_label         VARCHAR(200),
    ward_code          VARCHAR(10),
    province_code      VARCHAR(10),
    meetup_date        DATE          NOT NULL,
    time_mode          VARCHAR(10)   NOT NULL,
    -- EXACT only.
    starts_at          TIMESTAMP,
    visible_until      TIMESTAMP     NOT NULL,
    min_age            SMALLINT      NOT NULL DEFAULT 18,
    -- NULL means "80+", no upper bound.
    max_age            SMALLINT,
    join_policy        VARCHAR(10)   NOT NULL,
    -- Excluding the host; NULL means unlimited.
    max_participants   SMALLINT,
    status             VARCHAR(20)   NOT NULL,
    reject_reason      VARCHAR(40),
    review_note        VARCHAR(500),
    reviewed_by        UUID,
    reviewed_at        TIMESTAMP,
    submitted_at       TIMESTAMP     NOT NULL,
    resubmit_count     INT           NOT NULL DEFAULT 0,
    revision           INT           NOT NULL DEFAULT 1,
    cancel_reason      VARCHAR(300),
    cancelled_at       TIMESTAMP,
    data_version       BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_meetups_category CHECK (category IN (
        'FOOD_DRINK', 'COFFEE', 'NIGHTLIFE', 'OUTDOOR_ACTIVE', 'SIGHTSEEING',
        'ENTERTAINMENT', 'SHOPPING', 'WELLNESS', 'SOCIAL', 'OTHER')),
    CONSTRAINT chk_meetups_location_mode CHECK (location_mode IN ('AREA', 'EXACT')),
    CONSTRAINT chk_meetups_radius CHECK (
        (location_mode = 'AREA' AND radius_m IN (300, 1000, 3000))
        OR (location_mode = 'EXACT' AND radius_m IS NULL)),
    CONSTRAINT chk_meetups_time_mode CHECK (time_mode IN ('FLEXIBLE', 'EXACT')),
    CONSTRAINT chk_meetups_starts_at CHECK (
        (time_mode = 'EXACT' AND starts_at IS NOT NULL)
        OR (time_mode = 'FLEXIBLE' AND starts_at IS NULL)),
    CONSTRAINT chk_meetups_age CHECK (
        min_age BETWEEN 18 AND 99 AND (max_age IS NULL OR max_age BETWEEN min_age AND 99)),
    CONSTRAINT chk_meetups_join_policy CHECK (join_policy IN ('OPEN', 'APPROVAL')),
    CONSTRAINT chk_meetups_max_participants CHECK (
        max_participants IS NULL OR max_participants BETWEEN 1 AND 50),
    CONSTRAINT chk_meetups_status CHECK (status IN (
        'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED', 'REMOVED')),
    CONSTRAINT chk_meetups_reject_reason CHECK (reject_reason IS NULL OR reject_reason IN (
        'NOT_A_REAL_ACTIVITY', 'DATING_OR_SEXUAL', 'COMMERCIAL_OR_PROMOTION', 'CONTACT_INFO',
        'UNSAFE_LOCATION', 'ILLEGAL_OR_DANGEROUS', 'SENSITIVE_OR_POLITICAL', 'DUPLICATE_OR_SPAM',
        'INCORRECT_DETAILS', 'OTHER'))
);

-- Admin queue and the "visible now" filter.
CREATE INDEX IF NOT EXISTS idx_meetups_status_visible ON meetups (status, visible_until);
-- A host's own list and the per-host caps.
CREATE INDEX IF NOT EXISTS idx_meetups_host_created ON meetups (host_user_id, created_at DESC);
-- Discovery: bounding box over the displayed point of approved meetups.
CREATE INDEX IF NOT EXISTS idx_meetups_display_point
    ON meetups (display_latitude, display_longitude) WHERE status = 'APPROVED';

CREATE TABLE IF NOT EXISTS meetup_participants (
    meetup_id     UUID         NOT NULL REFERENCES meetups (id),
    user_id       UUID         NOT NULL REFERENCES users (id),
    status        VARCHAR(12)  NOT NULL,
    -- Optional note sent with a join request; only the host reads it.
    message       VARCHAR(140),
    requested_at  TIMESTAMP    NOT NULL,
    decided_at    TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (meetup_id, user_id),
    CONSTRAINT chk_meetup_participants_status CHECK (status IN (
        'REQUESTED', 'JOINED', 'DECLINED', 'WITHDRAWN', 'LEFT', 'REMOVED'))
);

-- A user's participations and their open-request cap.
CREATE INDEX IF NOT EXISTS idx_meetup_participants_user ON meetup_participants (user_id, status);

-- Business config (BusinessConfigKey MEETUP_*). The flag ships off; beta users always pass.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('MEETUP', 'ENABLED', 'false',
     'Bat tab Hoat dong (meetup) cho moi nguoi dung; beta user luon duoc dung. true/false', TRUE),
    ('MEETUP', 'MAX_ACTIVE_PER_HOST', '2',
     'So hoat dong dang cho duyet hoac dang hien toi da cua mot nguoi to chuc (1..10)', TRUE),
    ('MEETUP', 'MAX_SUBMISSIONS_PER_DAY', '5',
     'So lan gui duyet toi da cua mot nguoi trong 24 gio (1..50)', TRUE),
    ('MEETUP', 'MAX_DAYS_AHEAD', '3',
     'Ngay hoat dong toi da cach hom nay bao nhieu ngay, gio Viet Nam (0..14)', TRUE),
    ('MEETUP', 'EXACT_MIN_LEAD_MINUTES', '30',
     'Gio cu the phai cach hien tai it nhat bao nhieu phut (0..720)', TRUE),
    ('MEETUP', 'EXACT_VISIBLE_HOURS', '3',
     'Hoat dong gio cu the con hien tren ban do bao nhieu gio sau gio bat dau, toi da den nua dem (1..12)', TRUE),
    ('MEETUP', 'MAX_OPEN_REQUESTS_PER_USER', '5',
     'So yeu cau tham gia dang cho toi da cua mot nguoi (1..50)', TRUE),
    ('MEETUP', 'MAX_RESUBMITS', '2',
     'So lan sua va gui lai toi da sau khi bi tu choi (0..10)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- Console permissions. SUPER_ADMIN has no automatic grant, so it is granted explicitly.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES ('meetups', 'get'), ('meetups', 'review'), ('meetups', 'update')) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'meetups' AND p.action IN ('get', 'review', 'update')
WHERE r.code IN ('SUPER_ADMIN', 'MODERATOR')
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'meetups' AND p.action = 'get'
WHERE r.code = 'OPERATIONS'
ON CONFLICT DO NOTHING;
