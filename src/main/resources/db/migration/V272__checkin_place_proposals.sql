-- The place-linking agent now proposes catalogue places for check-ins too (epic 08). A human
-- approves each one from Telegram, the same way as for activities (V266): the proposal is an
-- AGENT_PROPOSAL item that names a check-in instead of an activity.
ALTER TABLE place_import_job_items ADD COLUMN checkin_id UUID;

-- One open proposal per check-in is enough; the agent re-runs over the same queue.
CREATE INDEX IF NOT EXISTS idx_place_import_job_items_checkin_pending
    ON place_import_job_items (checkin_id)
    WHERE approval_status = 'PENDING' AND checkin_id IS NOT NULL;

-- "🚫 Không phải địa điểm" in Telegram: the check-in stays as it is but leaves the queue of
-- check-ins waiting for a place.
ALTER TABLE user_checkins ADD COLUMN place_dismissed_at TIMESTAMP;
ALTER TABLE user_checkins ADD COLUMN place_dismissed_by UUID;
