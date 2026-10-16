-- The IANA zone the user's device last reported (X-Timezone header). Per-user calendar days
-- (daily quotas, "today") use it when a request carries no header, e.g. older app builds.
ALTER TABLE users ADD COLUMN IF NOT EXISTS time_zone VARCHAR(64);
