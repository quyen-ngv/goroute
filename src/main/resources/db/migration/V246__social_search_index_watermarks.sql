-- Feed search (epic 12 §4.3): the Lucene index of people, check-ins and trips catches up every
-- 30 seconds by reading the rows changed since its last pass, (updated_at, id) keyset. These
-- indexes make that read a short index range instead of a scan of three growing tables.
--
-- Plain CREATE INDEX on purpose, as in V214/V232/V242/V245: CONCURRENTLY would wait on the
-- transaction in which Flyway holds its lock.

CREATE INDEX IF NOT EXISTS idx_users_updated_at ON users (updated_at, id);
CREATE INDEX IF NOT EXISTS idx_user_checkins_updated_at ON user_checkins (updated_at, id);
CREATE INDEX IF NOT EXISTS idx_trips_updated_at ON trips (updated_at, id);
