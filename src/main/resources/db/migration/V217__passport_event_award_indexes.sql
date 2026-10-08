-- Re-projecting a passport tag's awards (admin creates or edits a tag) looked for qualifying
-- events by testing every row of passport_events. These two indexes let it start from the
-- tag's linked places and the Passport's provinces instead. See
-- .docs/BACKEND_PERF_PLAN_2026-10-07.md (F5) and PassportMapper.findTagAwardCandidates.
--
-- Plain CREATE INDEX on purpose (see V214): CONCURRENTLY would wait on Flyway's own
-- transaction. passport_events grows by one row per check-in, so both builds are short.

-- "Events at this place", and per user the count at each linked place (the HAVING clause).
CREATE INDEX IF NOT EXISTS idx_passport_events_place_user
    ON passport_events (place_id, user_id)
    WHERE place_id IS NOT NULL;

-- "Events in this province" across all users. idx_passport_events_province leads with user_id.
CREATE INDEX IF NOT EXISTS idx_passport_events_province_code
    ON passport_events (province_code)
    WHERE province_code IS NOT NULL;
