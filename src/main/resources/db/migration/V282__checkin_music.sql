-- Background music a check-in can carry (Facebook-style "add music" to photos/video).
--
-- Only the link is kept: audio_url points at the artist's own host and the app streams it
-- from there. Nothing is copied to our storage. Every track must allow commercial use and
-- syncing with pictures, which is CC0 or CC BY 4.0 -- never NC (commercial app), ND (syncing
-- music to moving pictures is an adaptation under CC 4.0) or SA (would bind the user's post).
-- CC BY needs the credit line in `attribution` shown with the post.
--
-- Seed: 40 tracks by Kevin MacLeod (incompetech.com, CC BY 4.0), 8 per mood.
-- No foreign keys, as in the rest of the schema.

CREATE TABLE IF NOT EXISTS music_tracks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(200) NOT NULL,
    artist           VARCHAR(200) NOT NULL,
    audio_url        VARCHAR(1000) NOT NULL,
    source_url       VARCHAR(1000) NOT NULL,
    license          VARCHAR(16) NOT NULL,
    attribution      VARCHAR(500) NOT NULL,
    mood             VARCHAR(16) NOT NULL,
    duration_seconds INT NOT NULL,
    sort_order       INT NOT NULL DEFAULT 0,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_music_tracks_license CHECK (license IN ('CC0', 'CC_BY_4')),
    CONSTRAINT ck_music_tracks_mood CHECK (mood IN ('CHILL', 'HAPPY', 'UPLIFTING', 'EPIC', 'GROOVY'))
);

CREATE INDEX IF NOT EXISTS idx_music_tracks_active ON music_tracks (sort_order) WHERE is_active = TRUE;

SET LOCAL lock_timeout = '10s';
ALTER TABLE user_checkins ADD COLUMN IF NOT EXISTS music_track_id UUID;

INSERT INTO config (label, key, value, description, is_active)
VALUES ('CHECKIN', 'MUSIC_ENABLED', 'false',
        'Cho moi nguoi gan nhac nen vao check-in (user beta luon duoc). true/false', TRUE)
ON CONFLICT (label, key) DO NOTHING;

INSERT INTO music_tracks (title, artist, audio_url, source_url, license, attribution, mood, duration_seconds, sort_order)
VALUES
    ('Carefree', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Carefree.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400037', 'CC_BY_4', '"Carefree" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 205, 10),
    ('Wallpaper', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Wallpaper.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100843', 'CC_BY_4', '"Wallpaper" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 220, 20),
    ('Life of Riley', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Life%20of%20Riley.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400054', 'CC_BY_4', '"Life of Riley" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 235, 30),
    ('Pamgaea', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Pamgaea.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1300036', 'CC_BY_4', '"Pamgaea" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 169, 40),
    ('Inspired', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Inspired.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1600022', 'CC_BY_4', '"Inspired" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 286, 50),
    ('Easy Lemon', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Easy%20Lemon.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1200076', 'CC_BY_4', '"Easy Lemon" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 126, 60),
    ('Dreamer', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Dreamer.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1600043', 'CC_BY_4', '"Dreamer" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 204, 70),
    ('Bossa Antigua', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Bossa%20Antigua.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1700069', 'CC_BY_4', '"Bossa Antigua" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'CHILL', 283, 80),
    ('Fluffing a Duck', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Fluffing%20a%20Duck.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100768', 'CC_BY_4', '"Fluffing a Duck" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 67, 90),
    ('Local Forecast', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Local%20Forecast.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1300010', 'CC_BY_4', '"Local Forecast" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 165, 100),
    ('Monkeys Spinning Monkeys', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Monkeys%20Spinning%20Monkeys.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400011', 'CC_BY_4', '"Monkeys Spinning Monkeys" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 125, 110),
    ('Cipher', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Cipher2.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100844', 'CC_BY_4', '"Cipher" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 231, 120),
    ('Hyperfun', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Hyperfun.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400038', 'CC_BY_4', '"Hyperfun" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 233, 130),
    ('Happy Alley', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Happy%20Alley.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100482', 'CC_BY_4', '"Happy Alley" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 80, 140),
    ('Overcast', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Overcast.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1200002', 'CC_BY_4', '"Overcast" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 228, 150),
    ('Heartwarming', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Heartwarming.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100207', 'CC_BY_4', '"Heartwarming" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'HAPPY', 72, 160),
    ('Funkorama', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Funkorama.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100474', 'CC_BY_4', '"Funkorama" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 201, 170),
    ('Brightly Fancy', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Brightly%20Fancy.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100883', 'CC_BY_4', '"Brightly Fancy" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 135, 180),
    ('Newer Wave', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Newer%20Wave.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2000024', 'CC_BY_4', '"Newer Wave" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 175, 190),
    ('Neon Laser Horizon', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Neon%20Laser%20Horizon.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2000023', 'CC_BY_4', '"Neon Laser Horizon" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 179, 200),
    ('Adding the Sun', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Adding%20the%20Sun.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1900041', 'CC_BY_4', '"Adding the Sun" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 176, 210),
    ('Flying Kerfuffle', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Flying%20Kerfuffle.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1800020', 'CC_BY_4', '"Flying Kerfuffle" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 250, 220),
    ('Angel Share', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Angel%20Share.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1700021', 'CC_BY_4', '"Angel Share" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 201, 230),
    ('Salty Ditty', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Salty%20Ditty.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1600053', 'CC_BY_4', '"Salty Ditty" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'UPLIFTING', 128, 240),
    ('Strength of the Titans', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Strength%20of%20the%20Titans.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1900052', 'CC_BY_4', '"Strength of the Titans" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 62, 250),
    ('Heroic Age', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Heroic%20Age.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100848', 'CC_BY_4', '"Heroic Age" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 97, 260),
    ('Dentaneosuchus Hunt', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Dentaneosuchus%20Hunt.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2500003', 'CC_BY_4', '"Dentaneosuchus Hunt" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 247, 270),
    ('Sauropod Spotting', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Sauropod%20Spotting.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2500002', 'CC_BY_4', '"Sauropod Spotting" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 225, 280),
    ('Cretaceous Dawn', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Cretaceous%20Dawn.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2500001', 'CC_BY_4', '"Cretaceous Dawn" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 245, 290),
    ('Sergio''s Magic Dustbin', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Sergio%27s%20Magic%20Dustbin.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2300012', 'CC_BY_4', '"Sergio''s Magic Dustbin" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 151, 300),
    ('Brain Dance', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Brain%20Dance.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2300006', 'CC_BY_4', '"Brain Dance" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 215, 310),
    ('Boogie Party', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Boogie%20Party.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2200002', 'CC_BY_4', '"Boogie Party" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'EPIC', 272, 320),
    ('Lobby Time', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Lobby%20Time.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1600054', 'CC_BY_4', '"Lobby Time" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 193, 330),
    ('Dispersion Relation', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Dispersion%20Relation.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100258', 'CC_BY_4', '"Dispersion Relation" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 244, 340),
    ('Smooth Lovin', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Smooth%20Lovin.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1700077', 'CC_BY_4', '"Smooth Lovin" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 259, 350),
    ('Lord of the Rangs', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Lord%20of%20the%20Rangs.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2300010', 'CC_BY_4', '"Lord of the Rangs" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 92, 360),
    ('Cloud Dancer', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Cloud%20Dancer.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2300007', 'CC_BY_4', '"Cloud Dancer" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 220, 370),
    ('Goblin Tinker Soldier Spy', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Goblin_Tinker_Soldier_Spy.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2300001', 'CC_BY_4', '"Goblin Tinker Soldier Spy" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 136, 380),
    ('Paradise Found', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Paradise_Found.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2300000', 'CC_BY_4', '"Paradise Found" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 187, 390),
    ('Adventures in Adventureland', 'Kevin MacLeod', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Adventures%20in%20Adventureland.mp3', 'https://incompetech.com/music/royalty-free/index.html?isrc=USUAN2100029', 'CC_BY_4', '"Adventures in Adventureland" Kevin MacLeod (incompetech.com). Licensed under Creative Commons: By Attribution 4.0 License', 'GROOVY', 261, 400);
