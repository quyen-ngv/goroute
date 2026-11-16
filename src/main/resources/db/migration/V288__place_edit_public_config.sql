-- Place edit suggestions: expose the flag and the pin cap to the app through
-- /v1/api/public/configs (same list as EVENT/ENABLED).
-- Split out of V286 because image 1.7.17 already shipped V286 without these two
-- statements; editing V286 would break Flyway validation wherever it ran.
UPDATE config
SET value = value || ', {label=''PLACE_EDIT'',key=''ENABLED''}',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'CONFIG' AND key = 'PUBLIC_CONFIG'
  AND value NOT LIKE '%label=''PLACE_EDIT'',key=''ENABLED''%';

UPDATE config
SET value = value || ', {label=''PLACE_EDIT'',key=''MAX_PIN_MOVE_METERS''}',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'CONFIG' AND key = 'PUBLIC_CONFIG'
  AND value NOT LIKE '%label=''PLACE_EDIT'',key=''MAX_PIN_MOVE_METERS''%';
