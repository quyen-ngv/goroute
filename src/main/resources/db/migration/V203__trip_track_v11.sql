-- Travel tracker v1.1 (.docs/designs/TRAVEL_TRACKER_API_CONTRACT.md, section "v1.1").
--
-- 1. Provinces a day's route passes through, computed with PostGIS when the day is rebuilt
--    (P4: recap, "provinces passed" on the profile). Kept apart from the passport's "visited"
--    provinces on purpose (TR-30): driving through a province earns no stamp.
-- 2. Off-plan stops (P2) reuse trip_track_visits (kind OFF_PLAN) from V202; only their
--    thresholds are new.
-- 3. A Travel Book page that draws the owner's recorded route.

ALTER TABLE trip_track_days
    ADD COLUMN IF NOT EXISTS province_codes JSONB NOT NULL DEFAULT '[]'::jsonb;

INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('TRACKER', 'OFFPLAN_RADIUS_M', '100', 'Diem dung ngoai lich: o trong ban kinh nay (m)', TRUE),
    ('TRACKER', 'OFFPLAN_MIN_MINUTES', '10', 'Diem dung ngoai lich: o lai it nhat (phut)', TRUE),
    ('TRACKER', 'OFFPLAN_NAME_RADIUS_M', '60', 'Dat ten diem dung bang dia diem danh muc trong ban kinh nay (m)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

-- Route page: the app draws the line itself from the "route" slot (no map tiles). Slot ids and
-- boxes match goroute_fe's book_skeletons.dart ('route_page'), which hides unknown slots.
INSERT INTO book_skeletons (id, skeleton_key, version, name, page_type, photo_count, canvas_width, canvas_height, slot_defs)
VALUES
('00000000-0000-0000-0000-000000203001', 'route_page', 1, 'Route page', 'route', 0, 375, 500, '[
  {"slotId":"background","type":"background","x":0,"y":0,"w":375,"h":500,"rotation":0,"zIndex":0},
  {"slotId":"trip_title","type":"text","x":42,"y":28,"w":290,"h":46,"rotation":0,"zIndex":5},
  {"slotId":"route","type":"route","x":24,"y":88,"w":327,"h":380,"rotation":0,"zIndex":3}
]'::jsonb)
ON CONFLICT (skeleton_key, version) DO NOTHING;
