-- A stamp rule's icon started life as an emoji ("🏅") or a glyph name ("flag"), so 100
-- characters was room to spare. It now holds the URL of an uploaded badge image, and a
-- storage URL runs past that limit: the admin upload succeeded while the save that
-- carried its URL was rejected, leaving the rule on its old placeholder glyph and the
-- app drawing the generic medal for a stamp that has artwork.
--
-- TEXT rather than a bigger VARCHAR, matching passport_tags.image_url, so the next
-- storage domain or file-naming change does not need a third migration.
ALTER TABLE passport_stamp_rules
    ALTER COLUMN icon TYPE TEXT;
