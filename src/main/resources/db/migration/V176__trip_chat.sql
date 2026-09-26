-- Trip group chat.
--
-- A trip conversation is the same thing as every other conversation, so it lives in the
-- same three tables rather than in a second chat system: one inbox, one realtime topic,
-- one set of read markers. Only the link and the type are new.

ALTER TABLE marketplace_conversations
    ADD COLUMN IF NOT EXISTS trip_id UUID REFERENCES trips(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS pinned_message_id UUID;

-- One conversation per trip. The service relies on this to make "create the trip chat"
-- idempotent under concurrent first opens rather than on a lock it would have to hold.
CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_conversation_trip
    ON marketplace_conversations(trip_id)
    WHERE trip_id IS NOT NULL;

ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_type;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_type
    CHECK (conversation_type IN ('DIRECT', 'HOTEL_BOOKING', 'ACTIVITY_ORDER', 'TRIP'));

-- Exactly one link per type, still. A TRIP row carries a trip and nothing else, which is
-- what lets the privacy rules key off the type alone.
ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_target;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_target CHECK (
        (conversation_type = 'DIRECT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL)
        OR (conversation_type = 'HOTEL_BOOKING' AND hotel_booking_id IS NOT NULL AND activity_order_id IS NULL AND trip_id IS NULL)
        OR (conversation_type = 'ACTIVITY_ORDER' AND hotel_booking_id IS NULL AND activity_order_id IS NOT NULL AND trip_id IS NULL)
        OR (conversation_type = 'TRIP' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NOT NULL)
    );

-- Reply-to, deliberately ON DELETE SET NULL: a quoted message that is later removed must
-- not take the reply with it.
ALTER TABLE marketplace_messages
    ADD COLUMN IF NOT EXISTS reply_to_message_id UUID REFERENCES marketplace_messages(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_marketplace_messages_reply_to
    ON marketplace_messages(reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;

-- One row per (message, person, emoji): the primary key is the "one reaction of a kind
-- per person" rule, so a double tap cannot double count.
CREATE TABLE IF NOT EXISTS marketplace_message_reactions (
    message_id UUID NOT NULL REFERENCES marketplace_messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, user_id, emoji)
);

CREATE INDEX IF NOT EXISTS idx_marketplace_message_reactions_message
    ON marketplace_message_reactions(message_id);

-- The inbox orders by last_message_at and filters by type; trip chats make that list long
-- enough for the index to matter.
CREATE INDEX IF NOT EXISTS idx_marketplace_conversations_type_last_message
    ON marketplace_conversations(conversation_type, last_message_at DESC);
