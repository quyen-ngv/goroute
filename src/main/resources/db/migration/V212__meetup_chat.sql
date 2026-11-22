-- Meetup group chat (epic 18).
--
-- A meetup gets one group conversation when it is created: the host is in it from the start,
-- and everybody whose participant row becomes JOINED is added to it. Like the trip chat (V176)
-- it lives in the same three chat tables, so the inbox, the realtime topic and the read
-- markers are shared; only the link and the type are new.

ALTER TABLE marketplace_conversations
    ADD COLUMN IF NOT EXISTS meetup_id UUID REFERENCES meetups(id);

-- One conversation per meetup. The service relies on this to make "create the meetup chat"
-- idempotent under a concurrent first open, exactly as the trip chat does.
CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_conversation_meetup
    ON marketplace_conversations(meetup_id)
    WHERE meetup_id IS NOT NULL;

ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_type;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_type
    CHECK (conversation_type IN ('DIRECT', 'HOTEL_BOOKING', 'ACTIVITY_ORDER', 'TRIP', 'MEETUP'));

-- Exactly one link per type, still: every existing branch from V176 now also requires
-- meetup_id IS NULL, and a MEETUP row carries a meetup and nothing else.
ALTER TABLE marketplace_conversations
    DROP CONSTRAINT IF EXISTS chk_marketplace_conversation_target;
ALTER TABLE marketplace_conversations
    ADD CONSTRAINT chk_marketplace_conversation_target CHECK (
        (conversation_type = 'DIRECT' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL)
        OR (conversation_type = 'HOTEL_BOOKING' AND hotel_booking_id IS NOT NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NULL)
        OR (conversation_type = 'ACTIVITY_ORDER' AND hotel_booking_id IS NULL AND activity_order_id IS NOT NULL AND trip_id IS NULL AND meetup_id IS NULL)
        OR (conversation_type = 'TRIP' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NOT NULL AND meetup_id IS NULL)
        OR (conversation_type = 'MEETUP' AND hotel_booking_id IS NULL AND activity_order_id IS NULL AND trip_id IS NULL AND meetup_id IS NOT NULL)
    );
