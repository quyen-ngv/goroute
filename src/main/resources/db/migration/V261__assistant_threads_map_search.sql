-- Pengoo (28/10/2026, .docs/designs/PENGOO_MAP_SEARCH_AND_THREADS_PLAN.md): several 1:1 threads
-- per person, a profile Pengoo keeps about the person across threads, and a map fallback for
-- search_places when the catalogue has no place by the asked name.

-- ---------------------------------------------------------------------------
-- 1. Threads. The one-per-person unique index goes; creation is serialised by a transaction
--    advisory lock on the owner instead (AssistantConversationService). A thread gets a title
--    from the model's first answer (or the user renames it) and is deleted by archiving it.
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS uq_marketplace_conversation_assistant;

ALTER TABLE marketplace_conversations
    ADD COLUMN IF NOT EXISTS title VARCHAR(80),
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

-- The owner's live threads, latest first: opening Pengoo, the thread list, the inbox row. NULLS
-- LAST as the queries sort it: a thread nobody wrote in yet comes after the ones in use.
CREATE INDEX IF NOT EXISTS idx_marketplace_conversations_assistant_owner
    ON marketplace_conversations (assistant_user_id, last_message_at DESC NULLS LAST)
    WHERE conversation_type = 'ASSISTANT' AND archived_at IS NULL;

-- ---------------------------------------------------------------------------
-- 2. What Pengoo remembers about a person in every 1:1 thread (home city, group, diet, pace...),
--    one line per fact, shown to and edited by the person. Per-thread notes stay in
--    assistant_memories. No physical foreign key, as for assistant_memories.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistant_profiles (
    user_id    UUID PRIMARY KEY,
    content    TEXT         NOT NULL,
    turn_id    UUID,
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 3. Runtime switch (BusinessConfigKey ASSISTANT_MAP_SEARCH_ENABLED).
-- ---------------------------------------------------------------------------
INSERT INTO config (label, key, value, description, is_active) VALUES
    ('ASSISTANT', 'MAP_SEARCH_ENABLED', 'true', 'Pengoo tim them tren ban do (Goong/Mapbox) khi danh muc co it hon 3 dia diem dung ten', TRUE)
ON CONFLICT DO NOTHING;
