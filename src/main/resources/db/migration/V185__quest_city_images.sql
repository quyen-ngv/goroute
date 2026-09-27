-- Quest platform: a quest is tagged to one or more city location-images (the curated city entities
-- the explore/home screens use), chosen first in the create flow. Stored on the version (immutable
-- snapshot, like amenity_tags) as a JSON array of location_images ids. Additive, forward-only.

ALTER TABLE quest_versions
    ADD COLUMN IF NOT EXISTS city_image_ids JSONB NOT NULL DEFAULT '[]'::jsonb;
