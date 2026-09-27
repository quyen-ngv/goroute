-- AR objects (§3.15): creators upload their own 3D objects from the app. A row with an owner is
-- private to that creator (only they can place it); a row without one is the console's shared
-- library. Additive, forward-only.
ALTER TABLE quest_ar_object_assets ADD COLUMN IF NOT EXISTS owner_user_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_quest_ar_object_assets_owner ON quest_ar_object_assets (owner_user_id);
