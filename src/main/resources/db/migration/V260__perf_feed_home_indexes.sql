-- Indexes for the Feed and Home queries tuned on 28/10/2026 (see .agents/docs/12-social-feed.md
-- and 06-places-discovery.md). Plain CREATE INDEX on purpose, as in V214: a concurrent build
-- waits for the transaction holding Flyway's advisory lock and would hang the migration.

-- Trip card memory previews (MediaAssetMapper#selectTripMemoryPreviewsByTripIds): one LIMITed
-- read per trip in gallery order. The expressions match the query's ORDER BY exactly.
CREATE INDEX IF NOT EXISTS idx_media_assets_trip_memory_preview
    ON media_assets (trip_id, (COALESCE(position, 32767)), (COALESCE(taken_at, created_at)) DESC, id)
    WHERE deleted_at IS NULL
      AND entity_type IN ('TRIP_MEMORY', 'TRIP_ACTIVITY_MEMORY')
      AND (asset_role IS NULL OR asset_role = 'MEMORY');

-- Public trips by when they were shared: the Feed's top-active board window and the Friends
-- tab's order (COALESCE(public_shared_at, created_at)).
CREATE INDEX IF NOT EXISTS idx_trips_public_effective_shared
    ON trips ((COALESCE(public_shared_at, created_at)) DESC)
    WHERE visibility = 'PUBLIC' AND is_deleted = FALSE;

-- A followed person's public check-ins newest first (Friends tab, profile lists).
CREATE INDEX IF NOT EXISTS idx_user_checkins_user_public
    ON user_checkins (user_id, created_at DESC, id DESC)
    WHERE visibility = 'PUBLIC' AND is_removed = FALSE;

-- Trending places (PlaceMapper#findTrendingUsage) now start from the area's places and look up
-- their recent activities: by catalogue id, or by the raw place id when there is none.
CREATE INDEX IF NOT EXISTS idx_activities_place_ref_created
    ON activities (place_ref_id, created_at)
    WHERE place_ref_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_activities_place_id_created
    ON activities (place_id, created_at)
    WHERE place_ref_id IS NULL;

-- Profile stats count a user's contributed places; the primary key leads with place_id.
CREATE INDEX IF NOT EXISTS idx_place_contributors_user
    ON place_contributors (user_id);
