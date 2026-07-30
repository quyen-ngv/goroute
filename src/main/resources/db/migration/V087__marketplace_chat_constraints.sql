CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_conversation_hotel_booking
    ON marketplace_conversations(hotel_booking_id)
    WHERE hotel_booking_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_conversation_activity_order
    ON marketplace_conversations(activity_order_id)
    WHERE activity_order_id IS NOT NULL;

ALTER TABLE marketplace_conversation_members
    ADD CONSTRAINT fk_marketplace_conversation_last_read_message
    FOREIGN KEY (last_read_message_id) REFERENCES marketplace_messages(id) ON DELETE SET NULL;
