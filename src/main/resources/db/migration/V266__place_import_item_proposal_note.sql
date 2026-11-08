-- AGENT_PROPOSAL import jobs: an AI agent proposes the Google Maps place for an unlinked trip
-- activity, and a human approves the link from Telegram. The agent's reason (why this place,
-- how sure) is what the approver reads before deciding, so it is kept on the item.
ALTER TABLE place_import_job_items ADD COLUMN proposal_note TEXT;

-- The agent re-runs over the same queue; one pending proposal per activity is enough.
CREATE INDEX IF NOT EXISTS idx_place_import_job_items_activity_pending
    ON place_import_job_items (activity_id)
    WHERE approval_status = 'PENDING' AND activity_id IS NOT NULL;
