-- Local events (epic 17): events shown on the app's Home "Sự kiện" tab, created only by admins in
-- the console or imported by an external AI crawler through POST /v1/api/admin/events/import.
--
-- Shape (SPEC_V2 §1):
--   * `local_events` is one row per event. Times are TIMESTAMP in UTC (repo convention); the event's
--     own IANA zone is kept in `timezone` so day logic can be done in local time. `title_norm` is the
--     title lower-cased, diacritics stripped, đ→d, single spaces — computed in Java, because
--     `unaccent` is not installed — and feeds the pg_trgm duplicate check.
--   * Imports are idempotent on (source, external_id). `content_hash` lets a re-import of the same
--     payload answer UNCHANGED without writing; `manually_edited` protects a console edit from the
--     next crawl.
--   * Images are never hotlinked: `local_event_images` rows start PENDING with the crawler's
--     `source_url` and a job re-hosts them through the SSRF-guarded download path.
--   * `admin_api_keys` are per-key machine credentials for the crawler (header X-Admin-Api-Key).
--     Only the SHA-256 of a key is stored; the key itself is shown once at creation.
-- No physical foreign keys, per project convention. Enum columns are VARCHAR + CHECK.
-- pg_trgm is already installed (V022, V029).

CREATE TABLE IF NOT EXISTS local_events (
    id                     UUID PRIMARY KEY,
    title                  VARCHAR(200)  NOT NULL,
    title_norm             VARCHAR(200)  NOT NULL,
    summary                VARCHAR(300),
    description            TEXT,
    language               VARCHAR(10),
    category               VARCHAR(30)   NOT NULL,
    tags                   JSONB         NOT NULL DEFAULT '[]'::jsonb,
    start_at               TIMESTAMP     NOT NULL,
    end_at                 TIMESTAMP     NOT NULL,
    end_at_estimated       BOOLEAN       NOT NULL DEFAULT FALSE,
    timezone               VARCHAR(50)   NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    all_day                BOOLEAN       NOT NULL DEFAULT FALSE,
    -- [{startAt, endAt, label, status}], times in UTC like the columns.
    sessions               JSONB         NOT NULL DEFAULT '[]'::jsonb,
    schedule_note          VARCHAR(300),
    lunar_date_text        VARCHAR(100),
    event_status           VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',
    previous_start_at      TIMESTAMP,
    attendance_mode        VARCHAR(10)   NOT NULL DEFAULT 'OFFLINE',
    online_url             VARCHAR(1000),
    venue_name             VARCHAR(200)  NOT NULL,
    address                VARCHAR(500),
    latitude               DECIMAL(10, 7) NOT NULL,
    longitude              DECIMAL(10, 7) NOT NULL,
    place_id               UUID,
    province_code          VARCHAR(10),
    ward_code              VARCHAR(10),
    is_free                BOOLEAN,
    price_min              BIGINT,
    price_max              BIGINT,
    currency               VARCHAR(3)    NOT NULL DEFAULT 'VND',
    price_note             VARCHAR(300),
    ticket_url             VARCHAR(1000),
    ticket_availability    VARCHAR(20)   NOT NULL DEFAULT 'UNKNOWN',
    organizer_name         VARCHAR(200),
    organizer_url          VARCHAR(1000),
    performers             JSONB         NOT NULL DEFAULT '[]'::jsonb,
    min_age                SMALLINT,
    -- Only ever a re-hosted URL on our storage, set from the position-0 image.
    cover_url              VARCHAR(1000),
    publish_status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    published_at           TIMESTAMP,
    source                 VARCHAR(50)   NOT NULL DEFAULT 'admin',
    external_id            VARCHAR(255),
    source_url             VARCHAR(1000),
    source_updated_at      TIMESTAMP,
    content_hash           VARCHAR(64),
    extraction_confidence  NUMERIC(4, 3),
    extracted_by           VARCHAR(100),
    raw_payload            JSONB,
    duplicate_of_event_id  UUID,
    manually_edited        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by             UUID,
    updated_by             UUID,
    is_deleted             BOOLEAN       NOT NULL DEFAULT FALSE,
    data_version           BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_local_events_category CHECK (category IN (
        'MUSIC', 'FESTIVAL', 'TRADITIONAL_FESTIVAL', 'PERFORMING_ARTS', 'EXHIBITION', 'FILM',
        'FOOD_DRINK', 'MARKET_FAIR', 'SPORTS', 'WORKSHOP', 'CONFERENCE', 'NIGHTLIFE', 'FAMILY_KIDS',
        'HOLIDAY_CELEBRATION', 'OTHER')),
    CONSTRAINT ck_local_events_event_status CHECK (event_status IN (
        'SCHEDULED', 'RESCHEDULED', 'POSTPONED', 'CANCELLED', 'MOVED_ONLINE')),
    CONSTRAINT ck_local_events_attendance_mode CHECK (attendance_mode IN ('OFFLINE', 'ONLINE', 'MIXED')),
    CONSTRAINT ck_local_events_ticket_availability CHECK (ticket_availability IN (
        'UNKNOWN', 'IN_STOCK', 'SOLD_OUT', 'PRE_ORDER')),
    CONSTRAINT ck_local_events_publish_status CHECK (publish_status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    CONSTRAINT ck_local_events_time_span CHECK (end_at >= start_at),
    CONSTRAINT ck_local_events_prices CHECK (
        (price_min IS NULL OR price_min >= 0)
        AND (price_max IS NULL OR price_max >= 0)
        AND (price_min IS NULL OR price_max IS NULL OR price_min <= price_max)),
    CONSTRAINT ck_local_events_min_age CHECK (min_age IS NULL OR min_age BETWEEN 0 AND 99),
    CONSTRAINT ck_local_events_confidence CHECK (
        extraction_confidence IS NULL OR extraction_confidence BETWEEN 0 AND 1)
);

-- The import's natural key. Console-created events have no external id and are not constrained.
CREATE UNIQUE INDEX IF NOT EXISTS uq_local_events_source_external
    ON local_events (source, external_id)
    WHERE external_id IS NOT NULL;

-- Public discovery: published, not deleted, not yet ended (end_at > from).
CREATE INDEX IF NOT EXISTS idx_local_events_publish_end
    ON local_events (publish_status, end_at)
    WHERE is_deleted = FALSE;

-- Radius search bounding-box prefilter, as for activity bookings.
CREATE INDEX IF NOT EXISTS idx_local_events_lat_lng
    ON local_events (latitude, longitude);

-- Fuzzy duplicate detection on import: similarity(title_norm, :t) within a start-time window.
CREATE INDEX IF NOT EXISTS idx_local_events_title_norm_trgm
    ON local_events USING GIN (title_norm gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_local_events_start
    ON local_events (start_at)
    WHERE is_deleted = FALSE;

-- Console list default order and the "duplicates only" filter.
CREATE INDEX IF NOT EXISTS idx_local_events_updated
    ON local_events (updated_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_local_events_duplicate_of
    ON local_events (duplicate_of_event_id)
    WHERE duplicate_of_event_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS local_event_images (
    id              UUID PRIMARY KEY,
    event_id        UUID          NOT NULL,
    position        SMALLINT      NOT NULL,
    -- Where the image came from (the crawler's URL, or our own URL for a console upload).
    source_url      VARCHAR(1000) NOT NULL,
    -- Our re-hosted copy; NULL until the job has stored it.
    url             VARCHAR(1000),
    credit          VARCHAR(200),
    status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    attempts        SMALLINT      NOT NULL DEFAULT 0,
    failure_reason  VARCHAR(300),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_local_event_images_status CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    CONSTRAINT ck_local_event_images_position CHECK (position >= 0)
);

CREATE INDEX IF NOT EXISTS idx_local_event_images_event
    ON local_event_images (event_id, position);

-- The re-hosting job polls the oldest pending rows.
CREATE INDEX IF NOT EXISTS idx_local_event_images_pending
    ON local_event_images (status, created_at)
    WHERE status = 'PENDING';

-- One row per non-dry-run import call: who sent it (a console session or an API key) and the totals.
CREATE TABLE IF NOT EXISTS local_event_import_batches (
    id             UUID PRIMARY KEY,
    actor_user_id  UUID      NOT NULL,
    api_key_id     UUID,
    dry_run        BOOLEAN   NOT NULL DEFAULT FALSE,
    total          INT       NOT NULL DEFAULT 0,
    created        INT       NOT NULL DEFAULT 0,
    updated        INT       NOT NULL DEFAULT 0,
    unchanged      INT       NOT NULL DEFAULT 0,
    skipped        INT       NOT NULL DEFAULT 0,
    failed         INT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_local_event_import_batches_created
    ON local_event_import_batches (created_at DESC);

CREATE TABLE IF NOT EXISTS local_event_import_items (
    id                     UUID PRIMARY KEY,
    batch_id               UUID         NOT NULL,
    item_index             INT          NOT NULL,
    source                 VARCHAR(50),
    external_id            VARCHAR(255),
    result                 VARCHAR(20)  NOT NULL,
    event_id               UUID,
    duplicate_of_event_id  UUID,
    errors                 JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_local_event_import_items_result CHECK (result IN (
        'CREATED', 'UPDATED', 'UNCHANGED', 'SKIPPED_EDITED', 'SKIPPED_DELETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_local_event_import_items_batch
    ON local_event_import_items (batch_id, item_index);

CREATE TABLE IF NOT EXISTS admin_api_keys (
    id             UUID PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    -- "grk_" plus the first random characters: enough to recognise a key in a list, useless to use.
    key_prefix     VARCHAR(16)  NOT NULL,
    -- Lower-case hex SHA-256 of the whole key. The key itself is never stored.
    key_hash       CHAR(64)     NOT NULL,
    -- The admin the key acts as: RBAC checks and created_by attribution use this user.
    owner_user_id  UUID         NOT NULL,
    scopes         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_by     UUID,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMP,
    last_used_at   TIMESTAMP,
    revoked_at     TIMESTAMP,
    CONSTRAINT uq_admin_api_keys_hash UNIQUE (key_hash)
);

CREATE INDEX IF NOT EXISTS idx_admin_api_keys_created
    ON admin_api_keys (created_at DESC);

-- Feature switch. Events are public data, so the server does not gate the endpoints; the app hides
-- the Home tab unless this is on or the user is in USER/BETA_USER.
INSERT INTO config (label, key, value, description, is_active)
VALUES ('EVENT', 'ENABLED', 'false',
        'Bat tab Su kien tren Home cho moi nguoi dung (beta user luon thay); true/false', TRUE)
ON CONFLICT (label, key) DO NOTHING;

UPDATE config
SET value = value || ', {label=''EVENT'',key=''ENABLED''}',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'CONFIG' AND key = 'PUBLIC_CONFIG'
  AND value NOT LIKE '%label=''EVENT''%';

-- Permissions. SUPER_ADMIN is granted explicitly: nothing grants new permissions to it automatically.
INSERT INTO admin_permissions(id, resource, action)
SELECT md5(resource || ':' || action)::uuid, resource, action
FROM (VALUES
    ('events', 'get'), ('events', 'create'), ('events', 'update'), ('events', 'delete'),
    ('events', 'import'),
    ('api-keys', 'manage')
) AS p(resource, action)
ON CONFLICT (resource, action) DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p
  ON (p.resource = 'events' AND p.action IN ('get', 'create', 'update', 'delete', 'import'))
  OR (p.resource = 'api-keys' AND p.action = 'manage')
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p
  ON p.resource = 'events' AND p.action IN ('get', 'create', 'update', 'delete', 'import')
WHERE r.code = 'CONTENT_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.resource = 'events' AND p.action = 'get'
WHERE r.code = 'OPERATIONS'
ON CONFLICT DO NOTHING;
