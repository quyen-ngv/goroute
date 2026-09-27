-- The app's update prompt reads these public rows (label APP). A build below MIN_BUILD_<PLATFORM>
-- must update; one below LATEST_BUILD_<PLATFORM> is offered the update. Build numbers are the part
-- after '+' in the app's version. 0 means "no check", so nothing is asked until an admin sets them
-- — and LATEST should only be raised once that build is live in the store.
-- ON CONFLICT DO NOTHING: values an admin already set are never overwritten.
INSERT INTO config (label, key, value, description, is_active)
VALUES
    ('APP', 'LATEST_BUILD_ANDROID', '0',
     'Build number moi nhat tren Google Play; app cu hon se duoc goi y cap nhat (0 = tat)', TRUE),
    ('APP', 'LATEST_BUILD_IOS', '0',
     'Build number moi nhat tren App Store; app cu hon se duoc goi y cap nhat (0 = tat)', TRUE),
    ('APP', 'MIN_BUILD_ANDROID', '0',
     'Build number Android thap nhat con ho tro; thap hon bat buoc cap nhat (0 = tat)', TRUE),
    ('APP', 'MIN_BUILD_IOS', '0',
     'Build number iOS thap nhat con ho tro; thap hon bat buoc cap nhat (0 = tat)', TRUE),
    ('APP', 'STORE_URL_ANDROID', 'https://play.google.com/store/apps/details?id=app.ondetour',
     'Link Google Play cho nut Cap nhat', TRUE),
    ('APP', 'STORE_URL_IOS', '',
     'Link App Store cho nut Cap nhat (https://apps.apple.com/app/id...)', TRUE),
    ('APP', 'UPDATE_NOTES', '',
     'Noi dung hien trong popup cap nhat (de trong = cau mac dinh)', TRUE)
ON CONFLICT (label, key) DO NOTHING;

UPDATE config
SET value = value
        || ', {label=''APP'',key=''LATEST_BUILD_ANDROID''}'
        || ', {label=''APP'',key=''LATEST_BUILD_IOS''}'
        || ', {label=''APP'',key=''MIN_BUILD_ANDROID''}'
        || ', {label=''APP'',key=''MIN_BUILD_IOS''}'
        || ', {label=''APP'',key=''STORE_URL_ANDROID''}'
        || ', {label=''APP'',key=''STORE_URL_IOS''}'
        || ', {label=''APP'',key=''UPDATE_NOTES''}',
    data_version = data_version + 1,
    updated_at = NOW()
WHERE label = 'CONFIG' AND key = 'PUBLIC_CONFIG'
  AND value NOT LIKE '%LATEST_BUILD_ANDROID%';
