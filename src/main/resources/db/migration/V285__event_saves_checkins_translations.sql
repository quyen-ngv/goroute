-- Epic 17: saving an event, checking in at one, putting one in a trip, reminders, and
-- machine translation of the event's text.

-- A saved event: private bookmark that drives the Feed strip and the reminders. Going to an
-- event (local_event_attendees) saves it too, so everybody going is reminded.
CREATE TABLE IF NOT EXISTS local_event_saves (
    event_id UUID      NOT NULL,
    user_id  UUID      NOT NULL,
    saved_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_local_event_saves_user ON local_event_saves (user_id, saved_at DESC);

INSERT INTO local_event_saves (event_id, user_id, saved_at)
SELECT event_id, user_id, joined_at FROM local_event_attendees
ON CONFLICT (event_id, user_id) DO NOTHING;

-- One row per reminder already sent, so the job can run every few minutes without repeating
-- itself. `kind` is the reminder (DAY_BEFORE, STARTING_SOON, OPENING_TODAY, ENDING_SOON) or a
-- status change with the UTC start it applied to (STATUS:CANCELLED:<yyyyMMddHHmm>).
CREATE TABLE IF NOT EXISTS local_event_reminders (
    event_id UUID         NOT NULL,
    user_id  UUID         NOT NULL,
    kind     VARCHAR(80)  NOT NULL,
    sent_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, user_id, kind)
);

-- Events cancelled, postponed or rescheduled before reminders existed are old news: mark them
-- as already announced to the people saved above, so the first run does not send them all.
-- The key matches LocalEventReminderService.statusKind.
INSERT INTO local_event_reminders (event_id, user_id, kind)
SELECT s.event_id, s.user_id,
       'STATUS:' || e.event_status || ':' || to_char(e.start_at, 'YYYYMMDDHH24MI')
FROM local_event_saves s
JOIN local_events e ON e.id = s.event_id
WHERE e.event_status IN ('CANCELLED', 'POSTPONED', 'RESCHEDULED')
ON CONFLICT (event_id, user_id, kind) DO NOTHING;

-- The event's free text in another language. AI rows are rewritten when the event's
-- content_hash moves; MANUAL rows (edited on the console) are never overwritten.
CREATE TABLE IF NOT EXISTS local_event_translations (
    event_id           UUID        NOT NULL,
    locale             VARCHAR(16) NOT NULL,
    summary            TEXT,
    description        TEXT,
    schedule_note      TEXT,
    price_note         TEXT,
    lunar_date_text    TEXT,
    translation_source VARCHAR(16) NOT NULL DEFAULT 'AI'
        CONSTRAINT ck_local_event_translations_source CHECK (translation_source IN ('AI', 'MANUAL')),
    status             VARCHAR(16) NOT NULL DEFAULT 'DONE'
        CONSTRAINT ck_local_event_translations_status CHECK (status IN ('DONE', 'FAILED')),
    source_hash        VARCHAR(64),
    attempts           INT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, locale)
);

SET LOCAL lock_timeout = '10s';

-- A check-in made at an event. The location stays inline on the check-in (the venue), and
-- place_id is still the venue's catalogue place when the event has one.
ALTER TABLE user_checkins ADD COLUMN IF NOT EXISTS local_event_id UUID;
CREATE INDEX IF NOT EXISTS idx_user_checkins_local_event
    ON user_checkins (local_event_id, visited_at DESC) WHERE local_event_id IS NOT NULL;

-- A trip stop that is an event.
ALTER TABLE activities ADD COLUMN IF NOT EXISTS local_event_id UUID;
CREATE INDEX IF NOT EXISTS idx_activities_local_event
    ON activities (local_event_id) WHERE local_event_id IS NOT NULL;

INSERT INTO config (label, key, value, description, is_active)
VALUES ('CHECKIN', 'EVENT_VERIFY_RADIUS_METERS', '150',
        'Ban kinh (met) quanh noi to chuc de check-in tai su kien khong co dia diem catalogue duoc tinh la da xac thuc. 20..5000', TRUE),
       ('EVENT', 'REMINDERS_ENABLED', 'true',
        'Gui thong bao nhac su kien cho nguoi da luu / tham gia. true/false', TRUE),
       ('EVENT', 'AUTO_TRANSLATE_ENABLED', 'true',
        'Tu dich tom tat, mo ta, ghi chu cua su kien da xuat ban sang cac ngon ngu cua app. true/false', TRUE)
ON CONFLICT (label, key) DO NOTHING;
