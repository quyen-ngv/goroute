-- Mapbox addresses in Vietnam carried a postcode where the province belongs
-- ("Núi Kho, Vũ Ninh, 16100, Việt Nam"). New answers are rebuilt with the ward and
-- province from the boundary dataset (MapboxResults.vietnamAddress); this cleans the
-- rows already kept, which a later Mapbox answer for the same place overwrites anyway.
UPDATE map_place_cache
SET address = regexp_replace(address, ', [0-9]{5,6}(,|$)', '\1', 'g'),
    updated_at = CURRENT_TIMESTAMP
WHERE provider = 'MAPBOX'
  AND country_code = 'VN'
  AND address ~ ', [0-9]{5,6}(,|$)';

-- Stored answers repeat the old addresses until they expire (up to 90 days): drop them.
DELETE FROM map_response_cache
WHERE provider = 'MAPBOX'
  AND operation IN ('AUTOCOMPLETE', 'REVERSE', 'GEOCODE');
