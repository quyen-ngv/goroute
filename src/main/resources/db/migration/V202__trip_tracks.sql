-- Travel tracker, phase 1 (.docs/designs/TRAVEL_TRACKER_TECH_PLAN.md §1.1).
--
-- A member of a trip can record the route they actually walk or ride. The phone buffers GPS
-- points and uploads them in chunks; the server keeps the raw chunks for a limited time
-- (TRACKER/RAW_RETENTION_DAYS) and derives one row per (trip, user, local day) with the
-- statistics and a simplified line, which is all any screen reads.
--
-- Points are stored per chunk (at most 500 points, columnar JSON), not one row per point: a
-- full day of recording is 1-2 thousand points per person, which as single rows would be tens
-- of millions of rows a month on one Postgres.
--
-- No foreign keys, per project convention. Enum columns are VARCHAR + CHECK.

CREATE TABLE IF NOT EXISTS trip_track_chunks (
    id                UUID PRIMARY KEY,
    trip_id           UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    client_chunk_id   UUID         NOT NULL,
    client_session_id UUID         NOT NULL,
    local_date        DATE         NOT NULL,
    started_at        TIMESTAMP    NOT NULL,
    ended_at          TIMESTAMP    NOT NULL,
    point_count       INT          NOT NULL,
    rejected_count    INT          NOT NULL DEFAULT 0,
    accuracy_mode     VARCHAR(16)  NOT NULL,
    source            VARCHAR(16)  NOT NULL,
    points            JSONB        NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    data_version      BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT ck_trip_track_chunks_accuracy_mode CHECK (accuracy_mode IN ('HIGH', 'BALANCED', 'SAVER')),
    CONSTRAINT ck_trip_track_chunks_source CHECK (source IN ('RECORD', 'AUTO')),
    -- A chunk that spans local midnight is stored as one row per day, so the client id
    -- alone is not unique; the pair is.
    CONSTRAINT uq_trip_track_chunks_client UNIQUE (user_id, client_chunk_id, local_date)
);

CREATE INDEX IF NOT EXISTS idx_trip_track_chunks_day
    ON trip_track_chunks (trip_id, user_id, local_date, started_at);
CREATE INDEX IF NOT EXISTS idx_trip_track_chunks_user
    ON trip_track_chunks (user_id);
CREATE INDEX IF NOT EXISTS idx_trip_track_chunks_purge
    ON trip_track_chunks (local_date);

CREATE TABLE IF NOT EXISTS trip_track_days (
    id                 UUID PRIMARY KEY,
    trip_id            UUID       NOT NULL,
    user_id            UUID       NOT NULL,
    local_date         DATE       NOT NULL,
    day_number         INT        NOT NULL,
    line_segments      JSONB      NOT NULL,
    bbox               JSONB      NULL,
    distance_m         INT        NOT NULL DEFAULT 0,
    flight_distance_m  INT        NOT NULL DEFAULT 0,
    moving_seconds     INT        NOT NULL DEFAULT 0,
    elevation_gain_m   INT        NOT NULL DEFAULT 0,
    elevation_loss_m   INT        NOT NULL DEFAULT 0,
    point_count        INT        NOT NULL DEFAULT 0,
    first_at           TIMESTAMP  NULL,
    last_at            TIMESTAMP  NULL,
    created_at         TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP  NOT NULL DEFAULT NOW(),
    data_version       BIGINT     NOT NULL DEFAULT 1,
    CONSTRAINT uq_trip_track_days UNIQUE (trip_id, user_id, local_date)
);

CREATE INDEX IF NOT EXISTS idx_trip_track_days_user
    ON trip_track_days (user_id);

CREATE TABLE IF NOT EXISTS trip_track_visits (
    id                  UUID PRIMARY KEY,
    trip_id             UUID           NOT NULL,
    user_id             UUID           NOT NULL,
    local_date          DATE           NOT NULL,
    kind                VARCHAR(16)    NOT NULL,
    activity_id         UUID           NULL,
    place_id            UUID           NULL,
    name                VARCHAR(255)   NULL,
    latitude            NUMERIC(10, 7) NOT NULL,
    longitude           NUMERIC(10, 7) NOT NULL,
    arrived_at          TIMESTAMP      NOT NULL,
    departed_at         TIMESTAMP      NULL,
    status              VARCHAR(16)    NOT NULL,
    created_activity_id UUID           NULL,
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    data_version        BIGINT         NOT NULL DEFAULT 1,
    CONSTRAINT ck_trip_track_visits_kind CHECK (kind IN ('ACTIVITY', 'OFF_PLAN')),
    CONSTRAINT ck_trip_track_visits_status CHECK (status IN ('VISITED', 'SUGGESTED', 'DISMISSED', 'ADDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_trip_track_visits_activity
    ON trip_track_visits (trip_id, user_id, activity_id, local_date)
    WHERE activity_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trip_track_visits_day
    ON trip_track_visits (trip_id, user_id, local_date);

CREATE TABLE IF NOT EXISTS trip_track_settings (
    id               UUID PRIMARY KEY,
    trip_id          UUID        NOT NULL,
    user_id          UUID        NOT NULL,
    visibility       VARCHAR(16) NOT NULL DEFAULT 'MEMBERS',
    trim_day_ends    BOOLEAN     NOT NULL DEFAULT TRUE,
    default_accuracy VARCHAR(16) NOT NULL DEFAULT 'BALANCED',
    auto_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    data_version     BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT ck_trip_track_settings_visibility CHECK (visibility IN ('PRIVATE', 'MEMBERS', 'PUBLIC_VIEWERS')),
    CONSTRAINT ck_trip_track_settings_accuracy CHECK (default_accuracy IN ('HIGH', 'BALANCED', 'SAVER')),
    CONSTRAINT uq_trip_track_settings UNIQUE (trip_id, user_id)
);

CREATE TABLE IF NOT EXISTS user_privacy_zones (
    id           UUID PRIMARY KEY,
    user_id      UUID           NOT NULL,
    name         VARCHAR(60)    NOT NULL,
    latitude     NUMERIC(10, 7) NOT NULL,
    longitude    NUMERIC(10, 7) NOT NULL,
    radius_m     INT            NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    data_version BIGINT         NOT NULL DEFAULT 1,
    CONSTRAINT ck_user_privacy_zones_radius CHECK (radius_m BETWEEN 200 AND 1000)
);

CREATE INDEX IF NOT EXISTS idx_user_privacy_zones_user
    ON user_privacy_zones (user_id, created_at);

-- Runtime knobs (TRACKER group). ON CONFLICT DO NOTHING: values an admin already set are kept.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('TRACKER', 'ENABLED', 'false', 'Bat tinh nang ghi hanh trinh cho moi nguoi (beta luon duoc bat)', TRUE),
    ('TRACKER', 'WINDOW_PAD_DAYS', '1', 'So ngay dem truoc/sau chuyen van nhan diem GPS', TRUE),
    ('TRACKER', 'MAX_ACCURACY_HIGH_M', '30', 'Bo diem co sai so lon hon (m) o muc Chinh xac cao', TRUE),
    ('TRACKER', 'MAX_ACCURACY_BALANCED_M', '60', 'Bo diem co sai so lon hon (m) o muc Can bang', TRUE),
    ('TRACKER', 'MAX_ACCURACY_SAVER_M', '150', 'Bo diem co sai so lon hon (m) o muc Tiet kiem pin', TRUE),
    ('TRACKER', 'DISTANCE_FILTER_HIGH_M', '10', 'Khoang cach giua hai diem (m) o muc Chinh xac cao', TRUE),
    ('TRACKER', 'DISTANCE_FILTER_BALANCED_M', '25', 'Khoang cach giua hai diem (m) o muc Can bang', TRUE),
    ('TRACKER', 'DISTANCE_FILTER_SAVER_M', '100', 'Khoang cach giua hai diem (m) o muc Tiet kiem pin', TRUE),
    ('TRACKER', 'MAX_SPEED_KMH', '300', 'Toc do (km/h) tren muc nay tren quang ngan la diem nhay GPS', TRUE),
    ('TRACKER', 'GAP_MINUTES', '10', 'Mat tin hieu lau hon (phut) va xa hon GAP_DISTANCE_M thi ve net dut', TRUE),
    ('TRACKER', 'GAP_DISTANCE_M', '1000', 'Khoang cach (m) de mot quang mat tin hieu thanh net dut', TRUE),
    ('TRACKER', 'FLIGHT_MIN_KM', '200', 'Buoc nhay toi thieu (km) de tinh la chang bay', TRUE),
    ('TRACKER', 'FLIGHT_MIN_KMH', '300', 'Toc do trung binh toi thieu (km/h) de tinh la chang bay', TRUE),
    ('TRACKER', 'STATIONARY_RADIUS_M', '30', 'Dung yen: moi diem nam trong ban kinh nay (m)', TRUE),
    ('TRACKER', 'STATIONARY_MINUTES', '3', 'Dung yen lau hon (phut) thi tu tam dung', TRUE),
    ('TRACKER', 'ARRIVAL_RADIUS_M', '100', 'Toi mot activity: trong ban kinh nay (m)', TRUE),
    ('TRACKER', 'ARRIVAL_MINUTES', '3', 'Toi mot activity: o lai it nhat (phut)', TRUE),
    ('TRACKER', 'NUDGE_COOLDOWN_MINUTES', '20', 'Khoang cach toi thieu giua hai lan nhac check-in (phut)', TRUE),
    ('TRACKER', 'NUDGE_DAILY_MAX', '6', 'So lan nhac check-in toi da moi ngay', TRUE),
    ('TRACKER', 'QUIET_START_HOUR', '22', 'Gio bat dau khong nhac check-in (0-23)', TRUE),
    ('TRACKER', 'QUIET_END_HOUR', '7', 'Gio ket thuc khong nhac check-in (0-23)', TRUE),
    ('TRACKER', 'LOW_BATTERY_PCT', '15', 'Pin duoi muc nay (%) thi chuyen sang Tiet kiem pin', TRUE),
    ('TRACKER', 'STOP_BATTERY_PCT', '5', 'Pin duoi muc nay (%) thi ket thuc phien ghi', TRUE),
    ('TRACKER', 'RAW_RETENTION_DAYS', '90', 'Giu diem GPS tho bao nhieu ngay sau ngay cuoi chuyen', TRUE),
    ('TRACKER', 'CHUNK_MAX_POINTS', '500', 'So diem toi da trong mot lan upload', TRUE)
ON CONFLICT (label, key) DO NOTHING;
