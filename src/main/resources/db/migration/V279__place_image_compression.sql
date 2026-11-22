-- Console place image compression (caesium). The compressed WebP overwrites each image file
-- under its existing key, so the URLs in thumbnail/images never change. This column only
-- records that the place was done and what it saved. NULL = never compressed.
--
-- A nullable column with no default is a catalogue-only change, but it still needs a brief
-- ACCESS EXCLUSIVE lock on places. Fail fast instead of queueing every places query behind a
-- long transaction of the running backend (the first deploy hit the 60 s socket timeout).
-- No index: the bulk run walks the primary key, and a full-table index build on places would
-- take longer than the migration may.
SET LOCAL lock_timeout = '10s';

ALTER TABLE places ADD COLUMN IF NOT EXISTS image_compression JSONB;
