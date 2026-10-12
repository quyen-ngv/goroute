-- Pengoo, the travel assistant that answers inside chat.
-- Design: .docs/designs/CHAT_AI_ASSISTANT_PLAN.md. Additive only; no physical foreign keys.

-- ---------------------------------------------------------------------------
-- 1. The assistant is a users row, so its messages carry a name, an avatar, reactions and
--    idempotency exactly like a person's. account_type tells it apart: it cannot sign in
--    (no password, no provider id, a .invalid address) and it is left out of Discover.
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(16) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_account_type;
ALTER TABLE users ADD CONSTRAINT chk_users_account_type
    CHECK (account_type IN ('HUMAN', 'ASSISTANT'));

-- The username is only claimed when nobody has it yet; the id is what the code relies on.
INSERT INTO users (id, email, password_hash, full_name, username, avatar_url, provider,
                   language, onboarding_completed, account_type, account_status, created_at, updated_at)
SELECT '5e9d0000-0000-4000-8000-00000000a1b0'::uuid, 'pengoo@assistant.vietdetour.invalid', NULL, 'Pengoo',
       CASE WHEN EXISTS (SELECT 1 FROM users WHERE lower(username) = 'pengoo') THEN NULL ELSE 'pengoo' END,
       NULL, 'LOCAL', 'vi', TRUE, 'ASSISTANT', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = '5e9d0000-0000-4000-8000-00000000a1b0'::uuid);

-- ---------------------------------------------------------------------------
-- 2. One private 1:1 conversation per person with the assistant.
-- ---------------------------------------------------------------------------
ALTER TABLE marketplace_conversations
    ADD COLUMN IF NOT EXISTS assistant_user_id UUID;

ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_type;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_type
    CHECK (conversation_type IN ('DIRECT', 'HOTEL_BOOKING', 'ACTIVITY_ORDER', 'TRIP', 'MEETUP', 'EVENT', 'ASSISTANT'));

-- Exactly one link per type, still; an ASSISTANT row carries its owner and nothing else.
ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_target;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_target CHECK (
        (conversation_type = 'DIRECT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NULL AND assistant_user_id IS NULL)
        OR (conversation_type = 'HOTEL_BOOKING' AND hotel_booking_id IS NOT NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NULL AND assistant_user_id IS NULL)
        OR (conversation_type = 'ACTIVITY_ORDER' AND hotel_booking_id IS NULL AND activity_order_id IS NOT NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NULL AND assistant_user_id IS NULL)
        OR (conversation_type = 'TRIP' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NOT NULL AND meetup_id IS NULL AND local_event_id IS NULL AND assistant_user_id IS NULL)
        OR (conversation_type = 'MEETUP' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NOT NULL AND local_event_id IS NULL AND assistant_user_id IS NULL)
        OR (conversation_type = 'EVENT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NOT NULL AND assistant_user_id IS NULL)
        OR (conversation_type = 'ASSISTANT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NULL AND assistant_user_id IS NOT NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_conversation_assistant
    ON marketplace_conversations (assistant_user_id)
    WHERE conversation_type = 'ASSISTANT';

-- ---------------------------------------------------------------------------
-- 3. The assistant's answers: Markdown text plus blocks (thinking, place cards, proposals,
--    suggestion chips) in attachments.
-- ---------------------------------------------------------------------------
ALTER TABLE marketplace_messages
    DROP CONSTRAINT IF EXISTS chk_marketplace_message_type;
ALTER TABLE marketplace_messages
    ADD CONSTRAINT chk_marketplace_message_type CHECK (message_type IN (
        'TEXT', 'IMAGE', 'FILE', 'SYSTEM', 'LOCATION', 'SHARE', 'ASSISTANT'));

-- ---------------------------------------------------------------------------
-- 4. One row per question. Doubles as the daily allowance ledger (counted rows of a user
--    since local midnight), the per-conversation queue, and the live "thinking" steps.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistant_turns (
    id                 UUID PRIMARY KEY,
    conversation_id    UUID         NOT NULL,
    trip_id            UUID,
    user_id            UUID         NOT NULL,
    trigger_message_id UUID,
    reply_message_id   UUID,
    status             VARCHAR(16)  NOT NULL,
    counted            BOOLEAN      NOT NULL DEFAULT FALSE,
    locale             VARCHAR(10),
    question           TEXT,
    steps              JSONB        NOT NULL DEFAULT '[]'::jsonb,
    error              TEXT,
    input_tokens       INTEGER,
    output_tokens      INTEGER,
    started_at         TIMESTAMP,
    finished_at        TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_assistant_turns_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_assistant_turns_user_created ON assistant_turns (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_assistant_turns_conversation_status ON assistant_turns (conversation_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_assistant_turns_open ON assistant_turns (status, started_at)
    WHERE status IN ('QUEUED', 'RUNNING');

-- ---------------------------------------------------------------------------
-- 5. Changes to a trip the assistant proposes. Nothing changes until a member who may edit
--    the trip applies one; that member, not the assistant, is the author of the change.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistant_proposals (
    id                 UUID PRIMARY KEY,
    conversation_id    UUID         NOT NULL,
    turn_id            UUID         NOT NULL,
    message_id         UUID,
    trip_id            UUID         NOT NULL,
    requested_by       UUID         NOT NULL,
    action             VARCHAR(16)  NOT NULL,
    payload            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    summary            TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    resolved_by        UUID,
    resolved_at        TIMESTAMP,
    result_activity_id UUID,
    failure_reason     TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_assistant_proposals_action CHECK (action IN ('ADD', 'MOVE', 'UPDATE_TIME', 'REMOVE')),
    CONSTRAINT chk_assistant_proposals_status
        CHECK (status IN ('PENDING', 'APPLIED', 'DISMISSED', 'FAILED', 'STALE'))
);
CREATE INDEX IF NOT EXISTS idx_assistant_proposals_conversation ON assistant_proposals (conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_assistant_proposals_trip ON assistant_proposals (trip_id);

-- ---------------------------------------------------------------------------
-- 6. Runtime switches (BusinessConfigKey ASSISTANT_*), seeded so the console lists them.
-- ---------------------------------------------------------------------------
INSERT INTO config (label, key, value, description, is_active) VALUES
    ('ASSISTANT', 'ENABLED', 'false', 'Bat tro ly Pengoo cho moi nguoi dung (beta users luon dung duoc)', TRUE),
    ('ASSISTANT', 'FREE_DAILY_QUESTIONS', '5', 'So cau hoi Pengoo moi ngay cho tai khoan FREE', TRUE),
    ('ASSISTANT', 'PRO_DAILY_QUESTIONS', '50', 'So cau hoi Pengoo moi ngay cho tai khoan PRO', TRUE),
    ('ASSISTANT', 'LOG_PAYLOADS', 'true', 'Luu prompt/response cua Pengoo vao ai_api_calls (chi bat trong beta)', TRUE),
    ('ASSISTANT', 'HISTORY_MESSAGES', '20', 'So tin nhan gan nhat gui kem moi cau hoi', TRUE),
    ('ASSISTANT', 'WELCOME_ENABLED', 'true', 'Pengoo gui tin chao mung khi tao chuyen di', TRUE),
    ('ASSISTANT', 'TURN_TIMEOUT_SECONDS', '150', 'Qua thoi gian nay ma Pengoo chua tra loi thi bao loi', TRUE)
ON CONFLICT DO NOTHING;
