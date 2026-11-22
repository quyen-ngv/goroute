-- V193 seeded STORE_URL_IOS empty, so an iOS update prompt had no button and a required update
-- could not block (the app never strands a user on a dialog with nowhere to go). Fill in the
-- App Store link. Only an empty value is replaced: a link an admin already set is kept.
-- LATEST_BUILD_* / MIN_BUILD_* stay as they are: they must name a build that is already live in
-- the store, so an admin raises them after each release.
UPDATE config
SET value = 'https://apps.apple.com/app/id6775022752',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'APP' AND key = 'STORE_URL_IOS'
  AND TRIM(COALESCE(value, '')) = '';
