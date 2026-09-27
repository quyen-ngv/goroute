-- Local-first play: a quest is downloaded whole (answers included) and played on the phone; the
-- finished run is uploaded once and replayed by the server, which re-grades every answer and
-- re-checks every arrival position before granting anything. `client_run_id` is the phone's id for
-- that run, so a retried upload records it once.
ALTER TABLE quest_runs ADD COLUMN IF NOT EXISTS client_run_id VARCHAR(64) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_quest_runs_client_run
    ON quest_runs (owner_user_id, client_run_id) WHERE client_run_id IS NOT NULL;
