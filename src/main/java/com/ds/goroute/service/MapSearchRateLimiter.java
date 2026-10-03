package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MapSearchUsageMapper;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps how many paid Goong search calls one user can make through the app's proxy.
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
 * A refused call is not forwarded and does not use up the daily allowance.
 *
 * <p>If the counter table cannot be reached the call is let through: a search that
 * works is worth more than a few uncounted requests while the database is in trouble.
 */
@Service
@Slf4j
public class MapSearchRateLimiter {

    static final ZoneId USAGE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BusinessConfigService config;
    private final MapSearchUsageMapper usageMapper;
    private final Clock clock;

    private final Cache<String, AtomicInteger> minuteWindows = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(200_000)
            .build();

    @Autowired
    public MapSearchRateLimiter(BusinessConfigService config, MapSearchUsageMapper usageMapper) {
        this(config, usageMapper, Clock.system(USAGE_ZONE));
    }

    MapSearchRateLimiter(BusinessConfigService config, MapSearchUsageMapper usageMapper, Clock clock) {
        this.config = config;
        this.usageMapper = usageMapper;
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
            reject(userId, today, operation, ErrorConstant.MAP_SEARCH_MINUTE_LIMIT_REACHED);
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
            reject(userId, today, operation, ErrorConstant.MAP_SEARCH_DAILY_LIMIT_REACHED);
        }
    }

    /** Deletes daily counters older than {@code MAP_SEARCH/USAGE_RETENTION_DAYS}. */
    public int purgeExpiredUsage() {
        int retentionDays = config.getInt(BusinessConfigKey.MAP_SEARCH_USAGE_RETENTION_DAYS);
        return usageMapper.deleteBefore(LocalDate.now(clock.withZone(USAGE_ZONE)).minusDays(retentionDays));
    }

    private void reject(UUID userId, LocalDate today, MapSearchOperation operation, int errorCode) {
        try {
            usageMapper.recordRejected(userId, today, operation.name());
        } catch (DataAccessException exception) {
            log.warn("Map search rejection not recorded for user {} ({}): {}",
                    userId, operation, exception.getMessage());
        }
        throw new BusinessException(errorCode);
    }
}
