-- Which trips the Feed may show (FeedTripRules): no trip that has not started yet, and none
-- with too few activities for its length. Both read at request time, so an edit applies at once.

INSERT INTO config (label, key, value, description, is_active)
VALUES ('FEED', 'TRIP_HIDE_UPCOMING', 'true',
        'An trip chua bat dau (ngay bat dau sau hom nay, gio Viet Nam) khoi Feed; trip dang di va da di van hien (true/false)', TRUE),
       ('FEED', 'TRIP_MIN_ACTIVITIES_PER_DAY', '2',
        'Trip tren Feed can it nhat bay nhieu activity moi ngay cua trip; 0 = tat (0-50, so thap phan)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
