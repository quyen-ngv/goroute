package com.ds.goroute.feed.event;

import com.ds.goroute.feed.model.FeedEventRow;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes feed_events (V267) off the request path: rows are queued in memory and flushed once a
 * second with Postgres COPY, so serving a Feed page never waits on this log. An analytics log,
 * not a source of truth: a crash loses at most the last second, a full queue drops rows (counted
 * and logged), and FEED.EVENTS_ENABLED off stops queueing. The backend runs as one instance.
 *
 * <p>COPY rather than a multi-row INSERT: measured locally on 2026-11-05, the MyBatis INSERT of
 * 1,000 rows wrote about 2,300 rows a second and dropped 75 % of a 9,600 rows/s burst.
 */
@Component
@Slf4j
public class FeedEventLogger {

    /** About 10 MB of rows; past it the newest rows are dropped. */
    static final int MAX_QUEUED = 50_000;
    /** Rows per COPY. */
    static final int FLUSH_BATCH = 10_000;

    private static final String COPY_SQL = "COPY feed_events (user_id, item_type, item_id, event, surface, variant, "
            + "session_id, position, dwell_ms, features) FROM STDIN WITH (FORMAT csv)";

    private final DataSource dataSource;
    private final BusinessConfigService config;

    private final Queue<FeedEventRow> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();

    public FeedEventLogger(@Qualifier("masterDataSource") DataSource dataSource, BusinessConfigService config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    public void log(List<FeedEventRow> rows) {
        if (rows == null || rows.isEmpty() || !enabled()) {
            return;
        }
        for (FeedEventRow row : rows) {
            if (queued.incrementAndGet() > MAX_QUEUED) {
                queued.decrementAndGet();
                dropped.incrementAndGet();
                continue;
            }
            queue.add(row);
        }
    }

    @Scheduled(fixedDelayString = "${goroute.jobs.feed-events-flush-ms:1000}")
    public void flush() {
        List<FeedEventRow> batch = new ArrayList<>();
        FeedEventRow row;
        while ((row = queue.poll()) != null) {
            queued.decrementAndGet();
            batch.add(row);
            if (batch.size() == FLUSH_BATCH) {
                write(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            write(batch);
        }
        long lost = dropped.getAndSet(0);
        if (lost > 0) {
            log.warn("Feed events queue full: {} rows dropped", lost);
        }
    }

    @PreDestroy
    void drain() {
        flush();
    }

    private void write(List<FeedEventRow> batch) {
        StringBuilder csv = new StringBuilder(batch.size() * 160);
        for (FeedEventRow row : batch) {
            csv.append(row.getUserId()).append(',')
                    .append(row.getItemType()).append(',')
                    .append(row.getItemId()).append(',');
            quoted(csv, row.getEvent()).append(',');
            quoted(csv, row.getSurface()).append(',');
            quoted(csv, row.getVariant()).append(',');
            quoted(csv, row.getSessionId()).append(',');
            number(csv, row.getPosition()).append(',');
            number(csv, row.getDwellMs()).append(',');
            quoted(csv, row.getFeatures()).append('\n');
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY_SQL, new StringReader(csv.toString()));
        } catch (Exception exception) {
            log.warn("Feed events not written, {} rows lost: {}", batch.size(), exception.getMessage());
        }
    }

    /** CSV field: null is an empty unquoted field (NULL), anything else is quoted. */
    private static StringBuilder quoted(StringBuilder csv, String value) {
        if (value == null) {
            return csv;
        }
        csv.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                csv.append('"');
            }
            csv.append(c);
        }
        return csv.append('"');
    }

    private static StringBuilder number(StringBuilder csv, Integer value) {
        return value == null ? csv : csv.append(value.intValue());
    }

    private boolean enabled() {
        try {
            return config.getBoolean(BusinessConfigKey.FEED_EVENTS_ENABLED);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
