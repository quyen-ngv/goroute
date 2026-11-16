-- What kind of spot a check-in is about, said by its author, for a check-in at a spot the
-- catalogue does not have (place_id NULL): ATTRACTION, DRINK or RESTAURANT. It lets the
-- Feed's place chips find such posts, which until now matched nothing because the chips
-- filter through the place.
--
-- A check-in with a catalogue place always takes the place's kind: every query reads the
-- place first and this column only when there is none, so an operator attaching a place
-- (POST /admin/checkins/{id}/place) moves the post to the place's chip without touching
-- this column. The author's word stays as a hint for whoever links the spot later.

ALTER TABLE user_checkins
    ADD COLUMN IF NOT EXISTS place_kind VARCHAR(20);

ALTER TABLE user_checkins
    DROP CONSTRAINT IF EXISTS chk_user_checkins_place_kind;
ALTER TABLE user_checkins
    ADD CONSTRAINT chk_user_checkins_place_kind
        CHECK (place_kind IS NULL OR place_kind IN ('ATTRACTION', 'DRINK', 'RESTAURANT'));

-- The place group a declared kind stands for (places.place_group).
CREATE OR REPLACE FUNCTION goroute_checkin_kind_group(p_kind TEXT) RETURNS VARCHAR
    LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE p_kind
               WHEN 'ATTRACTION' THEN 'ATTRACTIONS'
               WHEN 'DRINK' THEN 'FOOD_AND_DRINK'
               WHEN 'RESTAURANT' THEN 'FOOD_AND_DRINK'
           END
$$;

-- The place sub types a declared kind stands for (places.sub_types). DRINK is a café as far
-- as the chips go: the Drink chip asks for CAFE, TEA and BAR, and any one of them matches.
CREATE OR REPLACE FUNCTION goroute_checkin_kind_sub_types(p_kind TEXT) RETURNS VARCHAR[]
    LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE p_kind
               WHEN 'DRINK' THEN ARRAY['CAFE']::varchar[]
               WHEN 'RESTAURANT' THEN ARRAY['RESTAURANT']::varchar[]
               ELSE '{}'::varchar[]
           END
$$;
