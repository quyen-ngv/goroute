-- Meetup invitations (epic 18) and joining a local event with its group chat (epic 17).
--
-- 1. A meetup host may invite people they follow or who follow them. The invitation is a
--    participant row in the new status INVITED; accepting turns it into JOINED, declining into
--    WITHDRAWN, and the host takes it back the same way.
-- 2. A signed-in user may mark a published local event as "going". Everybody going is in the
--    event's group chat, a conversation of the new type EVENT in the shared chat tables, the
--    same way the trip (V176) and meetup (V212) groups are.

ALTER TABLE meetup_participants
    DROP CONSTRAINT IF EXISTS chk_meetup_participants_status;
ALTER TABLE meetup_participants
    ADD CONSTRAINT chk_meetup_participants_status CHECK (status IN (
        'REQUESTED', 'INVITED', 'JOINED', 'DECLINED', 'WITHDRAWN', 'LEFT', 'REMOVED'));

-- One row per person going. Leaving deletes the row: there is no history to keep, and going
-- again is a plain insert. No foreign keys, like the rest of the local event tables (V210).
CREATE TABLE IF NOT EXISTS local_event_attendees (
    event_id  UUID      NOT NULL,
    user_id   UUID      NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, user_id)
);

-- "Who is going", oldest first, and the count, both per event.
CREATE INDEX IF NOT EXISTS idx_local_event_attendees_event_joined
    ON local_event_attendees (event_id, joined_at);
-- The events one person is going to.
CREATE INDEX IF NOT EXISTS idx_local_event_attendees_user
    ON local_event_attendees (user_id, joined_at DESC);

ALTER TABLE marketplace_conversations
    ADD COLUMN IF NOT EXISTS local_event_id UUID;

-- One conversation per event; the service relies on it to create the chat idempotently under
-- a concurrent first open, exactly as the trip and meetup chats do.
CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_conversation_local_event
    ON marketplace_conversations (local_event_id)
    WHERE local_event_id IS NOT NULL;

ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_type;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_type
    CHECK (conversation_type IN ('DIRECT', 'HOTEL_BOOKING', 'ACTIVITY_ORDER', 'TRIP', 'MEETUP', 'EVENT'));

-- Exactly one link per type, still: every branch from V212 now also requires
-- local_event_id IS NULL, and an EVENT row carries an event and nothing else.
ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_target;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_target CHECK (
        (conversation_type = 'DIRECT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NULL)
        OR (conversation_type = 'HOTEL_BOOKING' AND hotel_booking_id IS NOT NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NULL)
        OR (conversation_type = 'ACTIVITY_ORDER' AND hotel_booking_id IS NULL AND activity_order_id IS NOT NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NULL)
        OR (conversation_type = 'TRIP' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NOT NULL AND meetup_id IS NULL AND local_event_id IS NULL)
        OR (conversation_type = 'MEETUP' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NOT NULL AND local_event_id IS NULL)
        OR (conversation_type = 'EVENT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL AND local_event_id IS NOT NULL)
    );
