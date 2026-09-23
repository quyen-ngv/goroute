-- Blocking: one person deciding another may not write to them.
--
-- Direct messages between two travellers are reachable from any profile, which means the
-- report-and-moderate loop is no longer enough on its own: reporting is what you do after
-- a message arrives, and blocking is what stops the next one. The two are complements, not
-- alternatives, and this table is the second half.
--
-- Deliberately not symmetric. A block is one person's decision about their own inbox, so
-- the row records who made it and about whom, and the other person is never told. Mutual
-- blocks are simply two rows.
--
-- Deliberately not applied to booking conversations. A guest who has paid must still be
-- able to reach the business they paid, and a business must still be able to answer; a
-- block that silenced a booking thread would turn a personal dispute into a commercial one.
-- The service enforces that distinction, and this comment is why it looks like an omission.

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id UUID        NOT NULL,
    blocked_id UUID        NOT NULL,
    -- Free text, never shown to the blocked person. Kept because moderators reviewing a
    -- report benefit from knowing whether the recipient had already blocked the sender.
    reason     VARCHAR(500),
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    -- Blocking yourself is meaningless and would make "can these two talk" answer no for a
    -- self-conversation. Refused here rather than only in the service, so no code path can
    -- introduce it.
    CONSTRAINT user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

-- The primary key already answers "has A blocked B", which is the question `send` asks.
-- This index answers the reverse, "who has blocked A", which is what the two-way check in
-- `start` needs and what a moderator reviewing an account looks at.
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON user_blocks (blocked_id);
