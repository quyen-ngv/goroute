-- Pengoo's running notes per conversation: what it must not forget once the facts scroll out of
-- the recent-message window (the trip's departure city, a member's diet, what was decided).
-- One row per conversation, rewritten by the worker when a turn adds something worth keeping.
CREATE TABLE IF NOT EXISTS assistant_memories (
    conversation_id UUID PRIMARY KEY,
    content         TEXT         NOT NULL,
    turn_id         UUID,
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO config (label, key, value, description, is_active) VALUES
    ('ASSISTANT', 'WEB_SEARCH_ENABLED', 'true', 'Cho Pengoo tim tren internet khi danh muc khong co ket qua (worker can co khoa API)', TRUE)
ON CONFLICT DO NOTHING;
