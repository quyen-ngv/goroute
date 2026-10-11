-- How much travellers use a place, the signal Home's top places and the trip suggestions
-- rank by (Google's stars are not trusted for that):
--   trip_use_count: trips (not deleted) that have an activity on the place, each trip once;
--                   activities in REJECTED status do not count.
--   checkin_count:  check-ins on the place that are not removed.
-- Kept by the backend after the fact (PlaceUsageStatsService): writes mark places changed,
-- a job recounts them every 30 seconds and every place once a night. Not updated in the
-- request, so a count can be a minute behind.

ALTER TABLE places
    ADD COLUMN IF NOT EXISTS trip_use_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS checkin_count INT NOT NULL DEFAULT 0;

ALTER TABLE places
    ADD CONSTRAINT ck_places_usage_counts_nonnegative
        CHECK (trip_use_count >= 0 AND checkin_count >= 0);

COMMENT ON COLUMN places.trip_use_count IS
    'Trips (not deleted) with a non-rejected activity on this place, each trip once. Recounted asynchronously.';
COMMENT ON COLUMN places.checkin_count IS
    'Check-ins on this place that are not removed. Recounted asynchronously.';

-- An activity points at a place by place_ref_id, or, without one, by place_id holding
-- either the catalogue id or the Google place id (02-trip-planning).
WITH activity_places AS (
    SELECT a.place_ref_id AS place_uuid, a.trip_id
    FROM activities a
    JOIN trips t ON t.id = a.trip_id AND t.is_deleted = FALSE
    WHERE a.place_ref_id IS NOT NULL
      AND COALESCE(a.status, '') <> 'REJECTED'
    UNION ALL
    SELECT p.id, a.trip_id
    FROM activities a
    JOIN trips t ON t.id = a.trip_id AND t.is_deleted = FALSE
    JOIN places p ON p.place_id = a.place_id
    WHERE a.place_ref_id IS NULL
      AND COALESCE(a.status, '') <> 'REJECTED'
    UNION ALL
    SELECT p.id, a.trip_id
    FROM activities a
    JOIN trips t ON t.id = a.trip_id AND t.is_deleted = FALSE
    JOIN places p ON a.place_id = p.id::text
    WHERE a.place_ref_id IS NULL
      AND COALESCE(a.status, '') <> 'REJECTED'
),
trip_uses AS (
    SELECT place_uuid, COUNT(DISTINCT trip_id) AS uses
    FROM activity_places
    GROUP BY place_uuid
)
UPDATE places p
SET trip_use_count = trip_uses.uses
FROM trip_uses
WHERE trip_uses.place_uuid = p.id;

WITH checkins AS (
    SELECT place_id AS place_uuid, COUNT(*) AS checkins
    FROM user_checkins
    WHERE is_removed = FALSE
      AND place_id IS NOT NULL
    GROUP BY place_id
)
UPDATE places p
SET checkin_count = checkins.checkins
FROM checkins
WHERE checkins.place_uuid = p.id;
