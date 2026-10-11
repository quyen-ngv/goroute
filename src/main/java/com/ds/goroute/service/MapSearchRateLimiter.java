package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MapSearchUsageMapper;
import com.ds.goroute.service.mapsearch.MapUsageNotifier;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MapSearchOperation;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps how many map calls one user can make through the server: search, place detail,
 * geocoding and routes. Every user has the same allowance.
 *
 * <p>Two limits per {@link MapSearchOperation}, both read from the {@code MAP_SEARCH}
 * config rows on every call so an operator's edit applies at once:
 * <ul>
 *   <li>per minute — a fixed one-minute window held in memory. It only has to stop a
 *       burst, so losing it on restart costs nothing; the backend runs as one instance.</li>
 *   <li>per day — counted in {@code map_search_usage_daily} by a single conditional upsert,
 *       so it survives restarts and two concurrent calls cannot both take the last slot.
 *       The day is the Vietnam calendar day.</li>
 * </ul>
 * A refused call is not forwarded and does not use up the daily allowance. The refusal (HTTP
 * 429) carries {@code scope}, {@code resetAt} and {@code retryAfterSeconds} so the app can
 * say when the user may search again.
 *
 * <p>The first refusal of the day for the daily limit tells the user by notification. A user
 * refused {@link #SPAM_ALERT_REFUSALS} times in one day for one kind of call is reported to
 * operators: a person stops long before that, a script does not.
 *
 * <p>If the counter table cannot be reached the call is let through: a search that
 * works is worth more than a few uncounted requests while the database is in trouble.
 */
@Service
@Slf4j
public class MapSearchRateLimiter {

    static final ZoneId USAGE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final long SPAM_ALERT_REFUSALS = 50;

    private final BusinessConfigService config;
    private final MapSearchUsageMapper usageMapper;
    private final MapUsageNotifier notifier;
    private final Clock clock;

    private final Cache<String, AtomicInteger> minuteWindows = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(200_000)
            .build();

    /**
     * Minute refusals and daily refusals share one counter, so "first daily refusal" is kept
     * here: one notification per user and kind of call per day, reset on restart at worst.
     */
    private final Cache<String, Boolean> dailyRefusalsSeen = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(1))
            .maximumSize(200_000)
            .build();

    @Autowired
    public MapSearchRateLimiter(BusinessConfigService config, MapSearchUsageMapper usageMapper,
                                MapUsageNotifier notifier) {
        this(config, usageMapper, notifier, Clock.system(USAGE_ZONE));
    }

    MapSearchRateLimiter(BusinessConfigService config, MapSearchUsageMapper usageMapper,
                         MapUsageNotifier notifier, Clock clock) {
        this.config = config;
        this.usageMapper = usageMapper;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * Takes one call from the user's allowance, or refuses it with HTTP 429.
     *
     * @param admin whether the caller holds a console role; exempt while
     *              {@code MAP_SEARCH/EXEMPT_ADMINS} is on
     */
    public void acquire(UUID userId, boolean admin, MapSearchOperation operation) {
        if (!config.getBoolean(BusinessConfigKey.MAP_SEARCH_RATE_LIMIT_ENABLED)) {
            return;
        }
        if (admin && config.getBoolean(BusinessConfigKey.MAP_SEARCH_EXEMPT_ADMINS)) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(USAGE_ZONE));

        long minute = clock.millis() / Duration.ofMinutes(1).toMillis();
        int perMinute = config.getInt(operation.perMinuteKey());
        int usedThisMinute = minuteWindows
                .get(userId + ":" + operation.name() + ":" + minute, ignored -> new AtomicInteger())
                .incrementAndGet();
        if (usedThisMinute > perMinute) {
            Instant nextMinute = Instant.ofEpochMilli((minute + 1) * Duration.ofMinutes(1).toMillis());
            reject(userId, today, operation, ErrorConstant.MAP_SEARCH_MINUTE_LIMIT_REACHED, "MINUTE", nextMinute);
        }

        int perDay = config.getInt(operation.perDayKey());
        boolean counted;
        try {
            counted = usageMapper.claimRequest(userId, today, operation.name(), perDay) > 0;
        } catch (DataAccessException exception) {
            log.warn("Map search usage not counted for user {} ({}); letting the call through: {}",
                    userId, operation, exception.getMessage());
            return;
        }
        if (!counted) {
            Instant tomorrow = today.plusDays(1).atStartOfDay(USAGE_ZONE).toInstant();
            reject(userId, today, operation, ErrorConstant.MAP_SEARCH_DAILY_LIMIT_REACHED, "DAY", tomorrow);
        }
    }

    /** Deletes daily counters older than {@code MAP_SEARCH/USAGE_RETENTION_DAYS}. */
    public int purgeExpiredUsage() {
        int retentionDays = config.getInt(BusinessConfigKey.MAP_SEARCH_USAGE_RETENTION_DAYS);
        return usageMapper.deleteBefore(LocalDate.now(clock.withZone(USAGE_ZONE)).minusDays(retentionDays));
    }

    private void reject(UUID userId, LocalDate today, MapSearchOperation operation, int errorCode,
                        String scope, Instant resetAt) {
        Long refusedToday = null;
        try {
            refusedToday = usageMapper.recordRejected(userId, today, operation.name());
        } catch (DataAccessException exception) {
            log.warn("Map search rejection not recorded for user {} ({}): {}",
                    userId, operation, exception.getMessage());
        }
        if (refusedToday != null) {
            tellPeople(userId, operation, scope, resetAt, refusedToday);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", operation.name());
        detail.put("scope", scope);
        detail.put("resetAt", resetAt.toString());
        detail.put("retryAfterSeconds", Math.max(1, Duration.between(clock.instant(), resetAt).toSeconds()));
        throw new BusinessException(errorCode, detail);
    }

    private void tellPeople(UUID userId, MapSearchOperation operation, String scope, Instant resetAt,
                            long refusedToday) {
        try {
            if ("DAY".equals(scope) && isFirstDailyRefusal(userId, operation)) {
                notifier.userReachedDailyLimit(userId, operation, resetAt);
            }
            if (refusedToday == SPAM_ALERT_REFUSALS) {
                notifier.suspectedSpam(userId, operation, refusedToday);
            }
        } catch (RuntimeException exception) {
            log.warn("Map limit notification failed for user {} ({}): {}", userId, operation, exception.getMessage());
        }
    }

    private boolean isFirstDailyRefusal(UUID userId, MapSearchOperation operation) {
        String key = userId + ":" + operation.name() + ":" + LocalDate.now(clock.withZone(USAGE_ZONE));
        return dailyRefusalsSeen.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }
}
