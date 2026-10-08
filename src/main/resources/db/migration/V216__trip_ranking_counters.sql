-- Ranking inputs for public trip search, stored on the trip instead of counted per row.
-- See .docs/BACKEND_PERF_PLAN_2026-10-07.md, Phase 7.
--
-- The Feed's "recommended" order scored every public trip with two correlated COUNT(*)
-- subqueries (activities and memory photos) before it could sort. These columns hold the
-- same two numbers:
--   activity_count: every activities row of the trip.
--   memory_count:   media_assets rows of the trip with entity_type TRIP_MEMORY or
--                   TRIP_ACTIVITY_MEMORY, asset_role NULL or MEMORY, not soft-deleted.
-- ActivityRepositoryImpl and TripMemoryServiceImpl recount them on every write;
-- TripContentCountReconcileJob repairs drift from writers that bypass those paths
-- (admin media library deletes) once a night.
--
-- "Has images" is not stored: it is a non-blank cover_image_url or memory_count > 0.

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS activity_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS memory_count INTEGER NOT NULL DEFAULT 0;

UPDATE trips t
SET activity_count = counted.item_count
FROM (
    SELECT trip_id, COUNT(*) AS item_count
    FROM activities
    WHERE trip_id IS NOT NULL
    GROUP BY trip_id
) counted
WHERE counted.trip_id = t.id;

UPDATE trips t
SET memory_count = counted.item_count
FROM (
    SELECT trip_id, COUNT(*) AS item_count
    FROM media_assets
    WHERE trip_id IS NOT NULL
      AND entity_type IN ('TRIP_MEMORY', 'TRIP_ACTIVITY_MEMORY')
      AND (asset_role IS NULL OR asset_role = 'MEMORY')
      AND deleted_at IS NULL
    GROUP BY trip_id
) counted
WHERE counted.trip_id = t.id;

-- Home "Friends" tab (TripMapper.findFollowingTrips): one followed owner's public trips,
-- newest share first. Same predicates and sort expression as the query.
CREATE INDEX IF NOT EXISTS idx_trips_owner_public_shared
    ON trips (owner_id, (COALESCE(public_shared_at, created_at)) DESC, id DESC)
    WHERE visibility = 'PUBLIC' AND is_deleted = FALSE;
