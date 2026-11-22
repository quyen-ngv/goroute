-- Finer kinds of a place (PlaceSubType): several per place, filterable in search and the console.
-- place_group stays the coarse tab; sub_types say "beach", "temple", "cafe", "halal" inside it.

ALTER TABLE places ADD COLUMN IF NOT EXISTS sub_types VARCHAR(40)[] NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_places_sub_types ON places USING GIN (sub_types);

COMMENT ON COLUMN places.sub_types IS
    'PlaceSubType codes. Food kinds only on FOOD_AND_DRINK, attraction kinds on the sightseeing groups, '
    'none on ACCOMMODATION. Validated in Java; written by import (only while empty) and admin edit.';

-- Backfill from the Google category and the old group. Whole-word matches only (\m \M): a
-- substring rule is what files "Barber shop" under bars and "Community center" under food.
-- ord is PlaceSubType declaration order, so stored arrays match what the application writes.
WITH rules (code, ord, family, pattern, exclude_pattern, group_hint) AS (
    VALUES
    ('NATURE',        1, 'ATTRACTION', '\m(nature|national park|nature preserve|mountain|mountain peak|waterfall|lake|forest|cave|hiking area|island|river|bay|valley|hot spring|rice terrace)\M', NULL, 'NATURE_AND_OUTDOORS'),
    ('BEACH',         2, 'ATTRACTION', '\m(beach|bãi biển)\M', NULL, NULL),
    ('PARK',          3, 'ATTRACTION', '\m(park|garden|botanical garden)\M', '\m(amusement|theme|water)\s+park\M', NULL),
    ('VIEWPOINT',     4, 'ATTRACTION', '\m(scenic spot|scenic point|observation deck|viewpoint|vista point|lookout)\M', NULL, NULL),
    ('SPIRITUAL',     5, 'ATTRACTION', '\m(temple|pagoda|church|cathedral|basilica|shrine|mosque|monastery|place of worship|chùa|đền|nhà thờ|miếu)\M', NULL, NULL),
    ('MUSEUM',        6, 'ATTRACTION', '\m(museum|art gallery|gallery)\M', NULL, NULL),
    ('HERITAGE',      7, 'ATTRACTION', '\m(historical landmark|historical place|heritage|monument|memorial|citadel|palace|fortress|castle|ruins|mausoleum)\M', NULL, NULL),
    ('LANDMARK',      8, 'ATTRACTION', '\m(landmark|tower|bridge|square|plaza)\M', NULL, NULL),
    ('SHOPPING',      9, 'ATTRACTION', '\m(shopping mall|mall|shopping center|department store|souvenir store|gift shop|boutique)\M', NULL, NULL),
    ('MARKET',       10, 'ATTRACTION', '\m(market|night market|chợ)\M', NULL, NULL),
    ('ENTERTAINMENT',11, 'ATTRACTION', '\m(entertainment|cinema|movie theater|theater|theatre|performing arts|karaoke|bowling alley|escape room|casino|arcade|zoo|aquarium)\M', NULL, NULL),
    ('THEME_PARK',   12, 'ATTRACTION', '\m(amusement park|theme park|water park)\M', NULL, NULL),
    ('RESTAURANT',   13, 'FOOD', '\mrestaurant\M', NULL, NULL),
    ('STREET_FOOD',  14, 'FOOD', '\m(street food|food stall|food court|hawker|eatery)\M', NULL, NULL),
    ('CAFE',         15, 'FOOD', '\m(cafe|café|coffee|espresso bar|cà phê)\M', NULL, NULL),
    ('TEA',          16, 'FOOD', '\m(tea|teahouse|bubble tea|milk tea|trà)\M', NULL, NULL),
    ('BAR',          17, 'FOOD', '\m(bar|pub|beer|brewery|brewpub|cocktail|lounge|izakaya)\M', '\m(snack|juice|espresso|salad|sushi|noodle|coffee|tea|dessert|oyster|smoothie|milk)\s+bar\M', NULL),
    ('BAKERY',       18, 'FOOD', '\m(bakery|patisserie|pastry shop|cake shop)\M', NULL, NULL),
    ('DESSERT',      19, 'FOOD', '\m(dessert|ice cream|gelato|frozen yogurt|sweets|confectionery|donut|chè)\M', NULL, NULL),
    ('SEAFOOD',      20, 'FOOD', '\m(seafood|oyster|crab|fish restaurant|hải sản)\M', NULL, NULL),
    ('VEGETARIAN',   21, 'FOOD', '\m(vegetarian|quán chay)\M', NULL, NULL),
    ('VEGAN',        22, 'FOOD', '\mvegan\M', NULL, NULL),
    ('HALAL',        23, 'FOOD', '\mhalal\M', NULL, NULL)
),
matched AS (
    SELECT p.id, array_agg(r.code ORDER BY r.ord)::varchar[] AS kinds
    FROM places p
    JOIN rules r
      ON ((r.family = 'FOOD' AND p.place_group = 'FOOD_AND_DRINK')
          OR (r.family = 'ATTRACTION' AND p.place_group IN
              ('ATTRACTIONS', 'CULTURE_AND_HERITAGE', 'NATURE_AND_OUTDOORS', 'SHOPPING_AND_MARKET', 'OTHER')))
     AND (p.category ~* r.pattern OR p.place_group = r.group_hint)
     AND (r.exclude_pattern IS NULL OR p.category IS NULL OR p.category !~* r.exclude_pattern)
    WHERE cardinality(p.sub_types) = 0
    GROUP BY p.id
)
UPDATE places p
SET sub_types = m.kinds
FROM matched m
WHERE p.id = m.id;

-- The old group is the only signal left for a culture or shopping place no word matched.
UPDATE places SET sub_types = ARRAY['HERITAGE']::varchar[]
WHERE place_group = 'CULTURE_AND_HERITAGE' AND cardinality(sub_types) = 0;

UPDATE places SET sub_types = ARRAY['SHOPPING']::varchar[]
WHERE place_group = 'SHOPPING_AND_MARKET' AND cardinality(sub_types) = 0;
