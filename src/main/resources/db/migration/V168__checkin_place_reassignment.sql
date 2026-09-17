-- Operators can move a check-in onto the right catalogue place (CHK-11 was "delete and
-- create again" for the author; this is the operator's equivalent, done in place).
--
-- The original location is never overwritten silently: every reassignment writes the full
-- pre-change snapshot here first, so "where did this check-in actually say it was?" stays
-- answerable after the catalogue row replaced it.
CREATE TABLE user_checkin_location_history (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkin_id        UUID NOT NULL REFERENCES user_checkins (id) ON DELETE CASCADE,

    previous_place_id UUID,
    new_place_id      UUID NOT NULL,

    -- Full snapshot of the location columns as they were: name, coordinates, the
    -- administrative parts, the source and the grouping key. JSONB rather than a copy of
    -- every column because this is read back for display, never filtered on.
    previous_location JSONB NOT NULL,

    reason            TEXT,
    changed_by        UUID,
    changed_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checkin_location_history_checkin
    ON user_checkin_location_history (checkin_id, changed_at DESC);
