-- Todo lists inside trip notes (epic 04 §2.4).
--
-- A note is either plain text or a todo list. A todo list keeps its title in
-- trip_notes.content and its items here. Any accepted member of the trip may add, edit,
-- tick and assign items; an item is done once for everyone, and done_by records who ticked
-- it. No foreign keys, as in the rest of the schema.

ALTER TABLE trip_notes ADD COLUMN IF NOT EXISTS note_type VARCHAR(16) NOT NULL DEFAULT 'TEXT';

CREATE TABLE IF NOT EXISTS trip_note_todo_items (
    id          UUID PRIMARY KEY,
    note_id     UUID NOT NULL,
    trip_id     UUID NOT NULL,
    text        VARCHAR(500) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    is_done     BOOLEAN NOT NULL DEFAULT FALSE,
    done_by     UUID,
    done_at     TIMESTAMP,
    created_by  UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_trip_note_todo_items_note
    ON trip_note_todo_items (note_id, sort_order) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS trip_note_todo_assignees (
    item_id     UUID NOT NULL,
    user_id     UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (item_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_trip_note_todo_assignees_user ON trip_note_todo_assignees (user_id);
