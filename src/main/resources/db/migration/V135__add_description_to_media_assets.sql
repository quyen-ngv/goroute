-- A memory image carries a short title and a longer description.
--
-- `caption` already holds what the viewer shows as the title; it keeps that job
-- so nothing already stored has to be moved. The description is new and starts
-- null everywhere, which is what the viewer renders as "no description".
ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS description TEXT;
