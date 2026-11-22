-- Evening-before weather alert for trip days (TRIP_DAY_WEATHER_ALERT).
-- Off for everyone at first; beta users (USER/BETA_USER) always get it.

INSERT INTO config (label, key, value, description, is_active)
VALUES ('TRIP_WEATHER', 'ALERT_ENABLED', 'false',
        'Gui canh bao thoi tiet luc 19:00 toi hom truoc cho moi thanh vien chuyen (user beta luon nhan). true/false', TRUE),
       ('TRIP_WEATHER', 'ALERT_RAIN_PERCENT', '80',
        'Kha nang mua (%) tu muc nay thi gui canh bao du khong co advisory. 50..100', TRUE)
ON CONFLICT (label, key) DO NOTHING;
