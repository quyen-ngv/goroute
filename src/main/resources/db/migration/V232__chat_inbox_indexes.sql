-- Chat inbox, tab badge and chat notification lookups.
--
-- Plain CREATE INDEX on purpose, as in V214: CONCURRENTLY would wait on the transaction
-- in which Flyway holds its lock.

-- "My conversations". The primary key leads with conversation_id, so the inbox and the
-- unread badge (re-read after every incoming message) scanned the whole member table.
CREATE INDEX IF NOT EXISTS idx_marketplace_conversation_members_user
    ON marketplace_conversation_members (user_id, conversation_id)
    WHERE left_at IS NULL;

-- A reader's chat notification for one conversation (one row per reader since V230) is
-- found, and marked read, by the conversation id inside data. Without this every message
-- read each recipient's whole notification history. created_at serves the newest-first lookup.
CREATE INDEX IF NOT EXISTS idx_notifications_conversation
    ON notifications (user_id, (data ->> 'conversationId'), created_at DESC)
    WHERE type IN ('MARKETPLACE_MESSAGE', 'CHAT_MENTION');
