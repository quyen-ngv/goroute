-- Quest platform: photos on a checkpoint. A creator attaches pictures that help a player find the
-- spot ("find the door in this photo"), shown on the current checkpoint while the player walks
-- there. Stored as the URLs the shared file-upload endpoint returns, like quest_versions.cover_url.
-- Additive, forward-only.

ALTER TABLE quest_checkpoints
    ADD COLUMN IF NOT EXISTS image_urls JSONB NOT NULL DEFAULT '[]'::jsonb;
