-- A place chip that asks for several sub types (Drinks: CAFE, TEA, BAR) summed
-- stop_kind_counts over them, so a stop tagged {CAFE, TEA} counted twice and a trip
-- with two such cafes passed chipTrips.minStops 3 (issue B140). stop_sub_types keeps
-- each linked stop's sub types, one array per stop, so the chip counts a stop once.
-- FeedItemMapper.xml upsertTrips writes it from now on; this fills the rows already there.
ALTER TABLE feed_items ADD COLUMN IF NOT EXISTS stop_sub_types JSONB;

UPDATE feed_items fi
SET stop_sub_types = s.stops
FROM (
    SELECT a.trip_id, jsonb_agg(to_jsonb(p.sub_types)) AS stops
    FROM activities a
    JOIN places p ON p.id = a.place_ref_id
    WHERE COALESCE(a.status, '') <> 'REJECTED'
      AND cardinality(p.sub_types) > 0
    GROUP BY a.trip_id
) s
WHERE fi.item_type = 'TRIP'
  AND fi.item_id = s.trip_id;
