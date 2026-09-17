-- Content the guest app needs to render the Klook-style tour and ticket detail pages.
--
-- Product (activity_bookings):
--   * good_to_know : ["..."]                  practical tips shown under "Good to know"
--   * faqs         : [{question, answer}]     ticket pages show an FAQ block
--   * video_url    : hero video, played from the gallery
--
-- Package (activity_packages):
--   * original_price : compare-at price shown struck through next to base_price
--   * package_group  : ticket packages sharing a group render under one heading
--   * group_type     : JOIN_IN | PRIVATE   (tour tag "Join in & private groups")
--   * departure_type : MEET_UP | PICK_UP   (tour "Departure type")
--   * details        : structured package page, shape documented on ActivityPackageDetails:
--       {areaName, durationMinutes, itinerary:[{kind,title,description,durationMinutes,label,images}],
--        itineraryNote, departure:{timeNote,title,description,times,notes}, returnInfo:{...},
--        eligibility:[], additionalInfo:[{title,items}], changeable, changePolicy,
--        usageValidity, howToUse:[]}
-- Tour vs ticket is decided by activity_type (ATTRACTION, EVENT, PASS are tickets); no column.

ALTER TABLE activity_bookings
    ADD COLUMN IF NOT EXISTS good_to_know JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS faqs JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS video_url TEXT;

ALTER TABLE activity_packages
    ADD COLUMN IF NOT EXISTS original_price DECIMAL(18,2),
    ADD COLUMN IF NOT EXISTS package_group VARCHAR(200),
    ADD COLUMN IF NOT EXISTS group_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS departure_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS details JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE activity_packages
    ADD CONSTRAINT chk_activity_package_original_price
        CHECK (original_price IS NULL OR original_price >= 0),
    ADD CONSTRAINT chk_activity_package_group_type
        CHECK (group_type IS NULL OR group_type IN ('JOIN_IN','PRIVATE')),
    ADD CONSTRAINT chk_activity_package_departure_type
        CHECK (departure_type IS NULL OR departure_type IN ('MEET_UP','PICK_UP'));
