-- Chat message notifications: one row per reader per conversation from now on
-- (ConversationNotifier revives the conversation's row, read or not, instead of adding one).
-- Collapse what piled up before: keep each reader's newest MARKETPLACE_MESSAGE row per
-- conversation and delete the older ones. Mentions (CHAT_MENTION) keep their own rows.

DELETE FROM notifications n
USING (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY user_id, data ->> 'conversationId' ORDER BY created_at DESC, id) AS rn
    FROM notifications
    WHERE type = 'MARKETPLACE_MESSAGE'
      AND data ->> 'conversationId' IS NOT NULL
) ranked
WHERE n.id = ranked.id
  AND ranked.rn > 1;
