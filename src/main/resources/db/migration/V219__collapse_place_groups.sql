-- place_group shrinks to the three tabs the app shows: FOOD_AND_DRINK, ATTRACTIONS, ACCOMMODATION.
-- What CULTURE_AND_HERITAGE / NATURE_AND_OUTDOORS / SHOPPING_AND_MARKET used to say now lives in
-- sub_types, which V218 already filled from these groups (HERITAGE, NATURE, SHOPPING fallbacks).
--
-- Ships with the PlaceGroup enum that no longer has those constants: EnumTypeHandler throws on
-- a value it does not know, so no row may keep an old code once this application version runs.

UPDATE places
SET place_group = 'ATTRACTIONS'
WHERE place_group IN ('CULTURE_AND_HERITAGE', 'NATURE_AND_OUTDOORS', 'SHOPPING_AND_MARKET', 'OTHER');

-- Anything else that is not one of the three is unreadable by the enum; treat it as unknown.
UPDATE places
SET place_group = NULL
WHERE place_group IS NOT NULL
  AND place_group NOT IN ('FOOD_AND_DRINK', 'ATTRACTIONS', 'ACCOMMODATION');

ALTER TABLE places
    ADD CONSTRAINT ck_places_place_group
        CHECK (place_group IS NULL OR place_group IN ('FOOD_AND_DRINK', 'ATTRACTIONS', 'ACCOMMODATION'));

COMMENT ON COLUMN places.place_group IS
    'Coarse tab: FOOD_AND_DRINK, ATTRACTIONS, ACCOMMODATION. Finer kinds are in sub_types (PlaceSubType).';
