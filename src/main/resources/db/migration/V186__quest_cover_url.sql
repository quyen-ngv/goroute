-- Quest platform: a quest's cover image. The version already has cover_media_id (a UUID FK to
-- media_assets, populated only by the async centralization job), but user quests upload a cover
-- through the shared file-upload endpoint, which returns a URL — the same way every other image in
-- the app is referenced. So we store that URL directly on the version snapshot alongside the id.
-- Additive, forward-only.

ALTER TABLE quest_versions
    ADD COLUMN IF NOT EXISTS cover_url VARCHAR(1024);
