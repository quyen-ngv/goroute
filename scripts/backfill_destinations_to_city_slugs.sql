-- =============================================================================
-- Backfill destinations â†’ city_slug (places, activity_bookings, location_images)
-- Cháº¡y thá»§ cÃ´ng trÃªn PostgreSQL (psql / DBeaver / pgAdmin).
--
-- Thá»© tá»±:
--   1) Cháº¡y Â§0a (táº¡o function normalize â€” báº¯t buá»™c trÆ°á»›c preview Â§0)
--   2) Â§0 preview (tuá»³ chá»n) â†’ Â§1 backup (tuá»³ chá»n)
--   3) Cháº¡y tá»« BEGIN (Â§2) Ä‘áº¿n COMMIT â€” backfill tháº­t
--   4) Â§6 kiá»ƒm tra sau COMMIT
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Â§0a HELPERS â€” cháº¡y TRÆ¯á»šC preview Â§0 (CREATE OR REPLACE, khÃ´ng Ä‘á»•i báº£ng)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION normalize_destination_key(input TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
SELECT regexp_replace(
    lower(
        translate(
            trim(COALESCE(input, '')),
            'Ã Ã¡áº£Ã£áº¡Äƒáº±áº¯áº³áºµáº·Ã¢áº§áº¥áº©áº«áº­Ã¨Ã©áº»áº½áº¹Ãªá»áº¿á»ƒá»…á»‡Ã¬Ã­á»‰Ä©á»‹Ã²Ã³á»Ãµá»Ã´á»“á»‘á»•á»—á»™Æ¡á»á»›á»Ÿá»¡á»£Ã¹Ãºá»§Å©á»¥Æ°á»«á»©á»­á»¯á»±á»³Ã½á»·á»¹á»µÄ‘Ã€Ãáº¢Ãƒáº Ä‚áº°áº®áº²áº´áº¶Ã‚áº¦áº¤áº¨áºªáº¬ÃˆÃ‰áººáº¼áº¸ÃŠá»€áº¾á»‚á»„á»†ÃŒÃá»ˆÄ¨á»ŠÃ’Ã“á»ŽÃ•á»ŒÃ”á»’á»á»”á»–á»˜Æ á»œá»šá»žá» á»¢Ã™Ãšá»¦Å¨á»¤Æ¯á»ªá»¨á»¬á»®á»°á»²Ãá»¶á»¸á»´Ä',
            'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuyyyyydAAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOOOUUUUUUUUUUYYYYYD'
        )
    ),
    '[^a-z0-9]',
    '',
    'g'
);
$$;

-- -----------------------------------------------------------------------------
-- Â§0 PREVIEW â€” sau Â§0a; khÃ´ng UPDATE báº£ng
-- -----------------------------------------------------------------------------
/*
SELECT 'places' AS tbl, elem AS raw, normalize_destination_key(elem) AS norm
FROM places p
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(p.destinations, '[]'::jsonb)) AS elem
LIMIT 50;

SELECT 'activity_bookings' AS tbl, elem AS raw, normalize_destination_key(elem) AS norm
FROM activity_bookings ab
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ab.destinations, '[]'::jsonb)) AS elem
LIMIT 50;
*/

-- -----------------------------------------------------------------------------
-- Â§1 BACKUP (khuyáº¿n nghá»‹)
-- -----------------------------------------------------------------------------
/*
CREATE TABLE IF NOT EXISTS _backup_places_destinations_20260523 AS
SELECT id, destinations FROM places;

CREATE TABLE IF NOT EXISTS _backup_activity_bookings_destinations_20260523 AS
SELECT id, destinations, destinations_norm FROM activity_bookings;

CREATE TABLE IF NOT EXISTS _backup_location_images_city_slug_20260523 AS
SELECT id, full_address, city_slug FROM location_images;
*/

BEGIN;

-- -----------------------------------------------------------------------------
-- Â§2 Helpers (transaction: alias table + map functions)
-- normalize_destination_key Ä‘Ã£ táº¡o á»Ÿ Â§0a
-- -----------------------------------------------------------------------------

-- Báº£ng alias: pattern Ä‘Ã£ normalize â†’ slug
CREATE TEMP TABLE IF NOT EXISTS _city_slug_alias (
    slug   TEXT NOT NULL,
    alias  TEXT NOT NULL,
    PRIMARY KEY (slug, alias)
) ON COMMIT DROP;

TRUNCATE _city_slug_alias;

-- alias = normalize_destination_key("HÃ  Ná»™i") â€” chá»‰ dÃ¹ng chuá»—i khÃ´ng dáº¥u, khÃ´ng space
INSERT INTO _city_slug_alias (slug, alias) VALUES
-- hanoi
('hanoi', 'hanoi'),
('hanoi', 'hni'),
('hanoi', 'hanoivietnam'),
('hanoi', 'vietnamhanoi'),
('hanoi', 'thudohanoi'),
('hanoi', 'thanhphohanoi'),
-- hcmc
('hcmc', 'hcmc'),
('hcmc', 'hcm'),
('hcmc', 'hochiminh'),
('hcmc', 'hochiminhcity'),
('hcmc', 'tphochiminh'),
('hcmc', 'tphcm'),
('hcmc', 'saigon'),
('hcmc', 'saigoncity'),
('hcmc', 'thanhphohochiminh'),
('hcmc', 'vietnamhochiminh'),
-- danang
('danang', 'danang'),
('danang', 'danangcity'),
('danang', 'thanhphodanang'),
-- hoian
('hoian', 'hoian'),
('hoian', 'hoianancienttown'),
('hoian', 'phocohoian'),
('hoian', 'hoianvietnam'),
-- hue
('hue', 'hue'),
('hue', 'huecity'),
('hue', 'thanhphohue'),
('hue', 'cothue'),
-- nhatrang
('nhatrang', 'nhatrang'),
('nhatrang', 'nhatrangcity'),
('nhatrang', 'thanhphonhatrang'),
-- phuquoc
('phuquoc', 'phuquoc'),
('phuquoc', 'phuquocisland'),
('phuquoc', 'daophuquoc');

-- Map 1 chuá»—i â†’ slug; NULL náº¿u khÃ´ng khá»›p
CREATE OR REPLACE FUNCTION map_destination_to_slug(raw TEXT)
RETURNS TEXT
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    n       TEXT;
    matched TEXT;
BEGIN
    n := normalize_destination_key(raw);
    IF n IS NULL OR n = '' THEN
        RETURN NULL;
    END IF;

    -- ÄÃ£ lÃ  slug chuáº©n
    IF n IN ('hanoi', 'hcmc', 'danang', 'hoian', 'hue', 'nhatrang', 'phuquoc') THEN
        RETURN n;
    END IF;

    -- Khá»›p chÃ­nh xÃ¡c alias
    SELECT a.slug INTO matched
    FROM _city_slug_alias a
    WHERE a.alias = n
    LIMIT 1;
    IF matched IS NOT NULL THEN
        RETURN matched;
    END IF;

    -- Khá»›p substring (alias >= 3 kÃ½ tá»±), Æ°u tiÃªn alias dÃ i
    SELECT a.slug INTO matched
    FROM _city_slug_alias a
    WHERE length(a.alias) >= 3
      AND (n LIKE '%' || a.alias || '%' OR a.alias LIKE '%' || n || '%')
    ORDER BY length(a.alias) DESC
    LIMIT 1;

    RETURN matched;
END;
$$;

-- Map cáº£ JSONB array â†’ JSONB array slug (distinct, bá» null)
CREATE OR REPLACE FUNCTION map_destinations_jsonb(arr JSONB)
RETURNS JSONB
LANGUAGE sql
STABLE
AS $$
SELECT COALESCE(
    jsonb_agg(DISTINCT slug ORDER BY slug),
    '[]'::jsonb
)
FROM (
    SELECT map_destination_to_slug(elem) AS slug
    FROM jsonb_array_elements_text(COALESCE(arr, '[]'::jsonb)) AS elem
) s
WHERE slug IS NOT NULL;
$$;

-- Giá»¯ slug Ä‘Ã£ map + pháº§n tá»­ cÅ© náº¿u chÆ°a map (Ä‘á»ƒ khÃ´ng máº¥t data; cÃ³ thá»ƒ xÃ³a sau)
CREATE OR REPLACE FUNCTION map_destinations_jsonb_keep_unmapped(arr JSONB)
RETURNS JSONB
LANGUAGE sql
STABLE
AS $$
SELECT COALESCE(
    jsonb_agg(DISTINCT val ORDER BY val),
    '[]'::jsonb
)
FROM (
    SELECT map_destination_to_slug(elem) AS val
    FROM jsonb_array_elements_text(COALESCE(arr, '[]'::jsonb)) AS elem
    UNION
    SELECT elem AS val
    FROM jsonb_array_elements_text(COALESCE(arr, '[]'::jsonb)) AS elem
    WHERE map_destination_to_slug(elem) IS NULL
      AND trim(elem) <> ''
) u
WHERE val IS NOT NULL AND trim(val) <> '';
$$;

-- destinations_norm giá»‘ng ActivityBookingSearchFieldHelper (Java)
CREATE OR REPLACE FUNCTION build_destinations_norm_from_jsonb(arr JSONB)
RETURNS TEXT
LANGUAGE sql
STABLE
AS $$
SELECT NULLIF(
    string_agg(DISTINCT normalize_destination_key(elem), '|' ORDER BY normalize_destination_key(elem)),
    ''
)
FROM jsonb_array_elements_text(COALESCE(arr, '[]'::jsonb)) AS elem
WHERE trim(elem) <> '';
$$;

-- -----------------------------------------------------------------------------
-- Â§3 location_images.city_slug (thÃªm cá»™t náº¿u chÆ°a cÃ³ â€” bá» qua náº¿u Ä‘Ã£ migration V042)
-- -----------------------------------------------------------------------------
ALTER TABLE location_images
    ADD COLUMN IF NOT EXISTS city_slug VARCHAR(100);

UPDATE location_images li
SET city_slug = map_destination_to_slug(li.full_address)
WHERE li.city_slug IS NULL
   OR trim(li.city_slug) = '';

-- -----------------------------------------------------------------------------
-- Â§4 places.destinations
-- -----------------------------------------------------------------------------
-- Chá»‰ slug (khuyáº¿n nghá»‹ cho food filter @> '["hanoi"]')
UPDATE places p
SET destinations = map_destinations_jsonb(p.destinations)
WHERE COALESCE(p.destinations, '[]'::jsonb) <> '[]'::jsonb;

-- Náº¿u muá»‘n GIá»® text cÅ© khi khÃ´ng map Ä‘Æ°á»£c, dÃ¹ng dÃ²ng nÃ y thay UPDATE trÃªn:
-- UPDATE places p
-- SET destinations = map_destinations_jsonb_keep_unmapped(p.destinations)
-- WHERE COALESCE(p.destinations, '[]'::jsonb) <> '[]'::jsonb;

-- -----------------------------------------------------------------------------
-- Â§5 activity_bookings.destinations + destinations_norm
-- -----------------------------------------------------------------------------
UPDATE activity_bookings ab
SET destinations = map_destinations_jsonb(ab.destinations)
WHERE COALESCE(ab.destinations, '[]'::jsonb) <> '[]'::jsonb;

UPDATE activity_bookings ab
SET destinations_norm = build_destinations_norm_from_jsonb(ab.destinations)
WHERE COALESCE(ab.destinations, '[]'::jsonb) <> '[]'::jsonb;

-- (Tuá»³ chá»n) Ä‘á»“ng bá»™ navigation_list náº¿u váº«n dÃ¹ng á»Ÿ chá»— khÃ¡c
UPDATE activity_bookings ab
SET navigation_list = ab.destinations
WHERE navigation_list IS DISTINCT FROM ab.destinations
  AND COALESCE(ab.destinations, '[]'::jsonb) <> '[]'::jsonb;

COMMIT;

-- -----------------------------------------------------------------------------
-- Â§6 KIá»‚M TRA SAU CHáº Y
-- -----------------------------------------------------------------------------

-- Pháº§n tá»­ destinations chÆ°a pháº£i slug trong 7 thÃ nh phá»‘
SELECT 'places_unmapped' AS report, p.id, p.title, elem AS raw_value
FROM places p
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(p.destinations, '[]'::jsonb)) AS elem
WHERE normalize_destination_key(elem) NOT IN
      ('hanoi','hcmc','danang','hoian','hue','nhatrang','phuquoc')
LIMIT 100;

SELECT 'activity_unmapped' AS report, ab.id, ab.title, elem AS raw_value
FROM activity_bookings ab
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ab.destinations, '[]'::jsonb)) AS elem
WHERE elem NOT IN ('hanoi','hcmc','danang','hoian','hue','nhatrang','phuquoc')
LIMIT 100;

SELECT slug, count(*) FROM (
    SELECT jsonb_array_elements_text(destinations) AS slug FROM places
    UNION ALL
    SELECT jsonb_array_elements_text(destinations) FROM activity_bookings
) t
GROUP BY 1
ORDER BY 2 DESC;

SELECT full_address, city_slug FROM location_images ORDER BY priority NULLS LAST;

-- -----------------------------------------------------------------------------
-- Â§7 Dá»ŒN FUNCTION (tuá»³ chá»n, sau khi verify)
-- -----------------------------------------------------------------------------
/*
DROP FUNCTION IF EXISTS map_destinations_jsonb_keep_unmapped(JSONB);
DROP FUNCTION IF EXISTS map_destinations_jsonb(JSONB);
DROP FUNCTION IF EXISTS build_destinations_norm_from_jsonb(JSONB);
DROP FUNCTION IF EXISTS map_destination_to_slug(TEXT);
DROP FUNCTION IF EXISTS normalize_destination_key(TEXT);
*/
