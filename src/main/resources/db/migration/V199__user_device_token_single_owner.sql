-- A push token names one app install, and one install is signed in as one person. The old
-- UNIQUE(user_id, fcm_token) let the same phone stay registered to everybody who ever signed in
-- on it, so the next account on a shared or handed-down phone kept receiving the previous
-- owner's pushes. Registering now moves the row to whoever registered last (ON CONFLICT on the
-- token), which needs the token itself to be unique.
--
-- Existing duplicates are resolved the same way: the most recently touched row wins, the rest are
-- the stale registrations the bug left behind. Safe to re-run: the second pass finds nothing to
-- delete and the index already exists.
DELETE FROM user_devices
WHERE id IN (
    SELECT ranked.id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY fcm_token
                   ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
               ) AS token_rank
        FROM user_devices
    ) ranked
    WHERE ranked.token_rank > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_devices_fcm_token ON user_devices (fcm_token);
