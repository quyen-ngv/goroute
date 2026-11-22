-- Feed platform step 2 (.docs/FEED_NEARBY_TIME_PLAN_2026-11-04.md §8.2, §12): an append-only log of
-- what the Feed served and what viewers did with it, for velocity, interest profiles, A/B and
-- later a learned ranking.
--
-- Additive only. feed_impressions (V245) stays exactly as it is: the Friends "seen" marks, the
-- seen filter, impression_count and affinity keep reading it, and old app builds keep posting
-- to /feed/impressions, which now writes both tables.
--
-- One row per event, not per (viewer, post): partitioned by day so retention is a DROP TABLE,
-- not a delete. No foreign key and no primary key on purpose: it is a log, rows are only ever
-- inserted in batches and read by time. No location column: the viewer's position is never stored.
CREATE TABLE IF NOT EXISTS feed_events (
    user_id    UUID        NOT NULL,
    item_type  VARCHAR(16) NOT NULL CHECK (item_type IN ('CHECKIN', 'REVIEW', 'TRIP')),
    item_id    UUID        NOT NULL,
    -- SERVE (the server ranked it onto a page), IMPRESSION, OPEN, and whatever the app sends later
    -- (GLANCE, LIKE, SAVE, HIDE, ...). An open list: no CHECK.
    event      VARCHAR(32) NOT NULL,
    -- for-you, near, friends, ... ; null for old app builds, which do not say.
    surface    VARCHAR(32),
    variant    VARCHAR(32),
    session_id VARCHAR(64),
    -- 0-based position in the ranking the post was served at.
    position   INT,
    dwell_ms   INT,
    -- SERVE only: the factor values the post was ranked with, {"v":1,"EQN":..,"F":..,"score":..}.
    features   JSONB,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- Catches rows when the daily job has not made the day's partition yet (the server was down
-- for a week); the job empties it by age like the rest.
CREATE TABLE IF NOT EXISTS feed_events_default PARTITION OF feed_events DEFAULT;

-- "What did this viewer do lately", for interest profiles. Declared on the parent, so every
-- partition gets it.
CREATE INDEX IF NOT EXISTS idx_feed_events_user_created ON feed_events (user_id, created_at);

-- Makes one partition per day from today to today + days_ahead and drops the ones older than
-- keep_days. A day whose rows already sit in the default partition is skipped with a notice.
-- Returns how many partitions were dropped. Called by FeedRetentionJob every night.
CREATE OR REPLACE FUNCTION feed_events_maintain(days_ahead INT, keep_days INT)
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE
    the_day DATE;
    part    RECORD;
    dropped INT := 0;
BEGIN
    FOR the_day IN
        SELECT generate_series(CURRENT_DATE, CURRENT_DATE + days_ahead, INTERVAL '1 day')::DATE
    LOOP
        BEGIN
            EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF feed_events FOR VALUES FROM (%L) TO (%L)',
                           'feed_events_p' || to_char(the_day, 'YYYYMMDD'), the_day, the_day + 1);
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'feed_events: no partition for %: %', the_day, SQLERRM;
        END;
    END LOOP;

    FOR part IN
        SELECT c.relname
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'feed_events'::regclass
          AND c.relname ~ '^feed_events_p[0-9]{8}$'
          AND to_date(substring(c.relname FROM 14), 'YYYYMMDD') < CURRENT_DATE - keep_days
    LOOP
        EXECUTE format('DROP TABLE %I', part.relname);
        dropped := dropped + 1;
    END LOOP;

    DELETE FROM feed_events_default WHERE created_at < CURRENT_DATE - keep_days;
    RETURN dropped;
END
$$;

SELECT feed_events_maintain(7, 30);

INSERT INTO config (label, key, value, description, is_active)
VALUES ('FEED', 'EVENTS_ENABLED', 'true',
        'Ghi feed_events (bai Feed da phuc vu + hanh vi nguoi xem); tat thi chi con feed_impressions', TRUE),
       ('FEED', 'EVENTS_RETENTION_DAYS', '30',
        'So ngay giu feed_events; partition theo ngay cu hon bi xoa moi dem (7-365)', TRUE),
       -- Feed platform step 3: the Lucene feed index (FeedLuceneIndex). Off until measured on the server.
       ('FEED', 'CANDIDATES_BACKEND', 'SQL',
        'Nguon ung vien Feed xep hang: SQL hoac LUCENE (index Lucene; tu quay ve SQL khi index chua san sang)', TRUE)
ON CONFLICT (label, key) DO NOTHING;
