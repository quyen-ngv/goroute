-- A chat message may now share a place, a dish, a check-in, a public trip, an event or a
-- meetup. The message stores only a reference in its one attachment,
-- {"shareType": "...", "targetId": "...", "citySlug": "..."}; every reader's app loads the item
-- itself, so the card stays current and the item's own visibility rule still applies per reader.

ALTER TABLE marketplace_messages
    DROP CONSTRAINT IF EXISTS chk_marketplace_message_type;
ALTER TABLE marketplace_messages
    ADD CONSTRAINT chk_marketplace_message_type CHECK (message_type IN (
        'TEXT', 'IMAGE', 'FILE', 'SYSTEM', 'LOCATION', 'SHARE'));
