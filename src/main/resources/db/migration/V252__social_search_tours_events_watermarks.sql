-- Feed search (epic 12 §4.4) now also lists tours (activity_bookings: imported catalogue and
-- partner products) and events (local_events). The index catches up every 30 seconds by
-- reading the rows changed since its last pass, (updated_at, id) keyset, as V246 does for
-- users, check-ins and trips. idx_local_events_updated (V210) is partial and DESC, so it does
-- not serve the keyset.
--
-- Plain CREATE INDEX on purpose, as in V246: CONCURRENTLY would wait on the transaction in
-- which Flyway holds its lock.

CREATE INDEX IF NOT EXISTS idx_activity_bookings_updated_at ON activity_bookings (updated_at, id);
CREATE INDEX IF NOT EXISTS idx_local_events_updated_id ON local_events (updated_at, id);
