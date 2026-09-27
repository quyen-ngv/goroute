-- Quest platform: a checkpoint's kind, drawn from the same list trip activities use
-- (restaurant, hotel, beach, attraction, shopping, spiritual, nature, entertainment), so the app
-- shows the same icon a trip would. Optional; validated in the service, not by a CHECK, so the
-- list can grow with the trip categories without a migration.
-- Additive, forward-only.

ALTER TABLE quest_checkpoints
    ADD COLUMN IF NOT EXISTS category VARCHAR(32) NULL;
