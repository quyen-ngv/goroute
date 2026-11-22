-- Activity names typed or pasted in the app ran past 255 characters and the insert failed with a
-- 500 ("value too long for type character varying(255)"). Widening a varchar is metadata-only in
-- Postgres, so this does not rewrite the table. CreateActivityRequest/UpdateActivityRequest cap
-- the name at the same 1000 so a longer one is a 400, not a 500.
ALTER TABLE activities ALTER COLUMN name TYPE VARCHAR(1000);
