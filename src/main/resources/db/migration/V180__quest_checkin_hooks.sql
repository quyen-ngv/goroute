-- Quest platform, phase 3: the hooks the play flow needs on the existing check-in / passport
-- rails (§6.1, §6.2.2). Trap #4: two CHECK constraints hard-list their allowed values, so an
-- INSERT with a new value fails until they are widened. Additive and backward-compatible.

-- A quest check-in is a real user_checkins row that also points back at the run it happened in,
-- so the Feed can group one card per run per player (§3.8) and the run can find its check-ins.
ALTER TABLE user_checkins ADD COLUMN IF NOT EXISTS quest_run_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_user_checkins_quest_run
    ON user_checkins (quest_run_id) WHERE quest_run_id IS NOT NULL;

-- Widen the location-source CHECK (V119 listed six values) to accept a quest checkpoint.
ALTER TABLE user_checkins DROP CONSTRAINT IF EXISTS ck_user_checkin_location_source;
ALTER TABLE user_checkins ADD CONSTRAINT ck_user_checkin_location_source
    CHECK (location_source IN ('CATALOGUE_PLACE', 'TRIP_ACTIVITY', 'MAP_SEARCH',
                               'REVERSE_GEOCODE', 'USER_NAMED', 'COORDINATES_ONLY',
                               'QUEST_CHECKPOINT'));

-- Widen the passport-event source CHECK (V120 listed two values) to accept a quest run, so
-- completing a quest can write exactly one passport_events row via the existing rail.
ALTER TABLE passport_events DROP CONSTRAINT IF EXISTS ck_passport_event_source;
ALTER TABLE passport_events ADD CONSTRAINT ck_passport_event_source
    CHECK (source IN ('ACTIVITY_CHECKIN', 'USER_CHECKIN', 'QUEST_RUN'));
