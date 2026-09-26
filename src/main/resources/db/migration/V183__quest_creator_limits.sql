-- Quest platform, phase 5: the state the submission limits read (§3.2, §8). D4 opens creation to
-- everyone, so these are the only things throttling the review queue: a cooldown between
-- submissions, and a denial streak that blocks a creator who keeps submitting bad quests.

ALTER TABLE quest_creators ADD COLUMN IF NOT EXISTS last_submitted_at TIMESTAMP NULL;
ALTER TABLE quest_creators ADD COLUMN IF NOT EXISTS denied_streak INT NOT NULL DEFAULT 0;
ALTER TABLE quest_creators ADD COLUMN IF NOT EXISTS blocked_until TIMESTAMP NULL;
