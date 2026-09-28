-- A creator chooses whether the whole route is shown from the start (the detail page and every
-- checkpoint ahead on the play map) or revealed one checkpoint at a time (§3.8). Shown by default.
ALTER TABLE quest_versions ADD COLUMN IF NOT EXISTS reveal_route BOOLEAN NOT NULL DEFAULT TRUE;
