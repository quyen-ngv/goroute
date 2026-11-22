package com.ds.goroute.service.mapsearch;

import com.ds.goroute.mapper.MapProviderUsageMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MapBillingApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The system-wide ceiling on paid Mapbox calls: whatever any user or script does, the bill
 * stops at the caps in {@code MAP_BUDGET}. Periods are UTC calendar months and days.
 *
 * <p>Each paid call takes one unit from today and from this month. When either is spent the
 * call is refused and the caller answers from its caches ("saving mode") until the period
 * ends. Operators are told once when the month crosses {@code WARN_PERCENT}, and once per
 * period when a cap is reached.
 *
 * <p>If the counter table cannot be reached the call is let through, as the per-user limiter
 * does: a few uncounted calls cost less than a map that stops working.
 */
@Slf4j
@Service
public class MapBudgetGuard {

    /** Whether the call may go out, and when saving mode ends if it may not. */
    public record Decision(boolean allowed, Instant resumesAt) {
        static Decision allow() {
            return new Decision(true, null);
        }
    }

    private final BusinessConfigService config;
    private final MapProviderUsageMapper usageMapper;
    private final MapUsageNotifier notifier;
    private final Clock clock;

    @Autowired
    public MapBudgetGuard(BusinessConfigService config, MapProviderUsageMapper usageMapper, MapUsageNotifier notifier) {
        this(config, usageMapper, notifier, Clock.systemUTC());
    }

    MapBudgetGuard(BusinessConfigService config, MapProviderUsageMapper usageMapper, MapUsageNotifier notifier,
                   Clock clock) {
        this.config = config;
        this.usageMapper = usageMapper;
        this.notifier = notifier;
        this.clock = clock;
    }

    public Decision tryAcquire(MapBillingApi api) {
        if (!config.getBoolean(BusinessConfigKey.MAP_BUDGET_ENABLED)) {
            return Decision.allow();
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate month = today.withDayOfMonth(1);
        long monthCap = config.getInt(api.monthlyCapKey());
        long dayCap = Math.max(1, monthCap * config.getInt(BusinessConfigKey.MAP_BUDGET_DAILY_CAP_PERCENT) / 100);
        Instant tomorrow = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextMonth = month.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (monthCap <= 0) {
            return refuse(api, "MONTH", month, nextMonth);
        }
        try {
            if (usageMapper.claim("DAY", today, api.name(), dayCap) == null) {
                return refuse(api, "DAY", today, tomorrow);
            }
            Long monthCount = usageMapper.claim("MONTH", month, api.name(), monthCap);
            if (monthCount == null) {
                usageMapper.release("DAY", today, api.name());
                return refuse(api, "MONTH", month, nextMonth);
            }
            long warnAt = (long) Math.ceil(monthCap * config.getInt(BusinessConfigKey.MAP_BUDGET_WARN_PERCENT) / 100.0);
            // Counts move one at a time, so exactly one call sees the threshold value.
            if (monthCount == warnAt) {
                notifier.budgetWarning(api, monthCount, monthCap);
            }
            return Decision.allow();
        } catch (DataAccessException exception) {
            log.warn("Map budget not counted for {}; letting the call through: {}", api, exception.getMessage());
            return Decision.allow();
        }
    }

    private Decision refuse(MapBillingApi api, String periodKind, LocalDate periodStart, Instant resumesAt) {
        try {
            Long refused = usageMapper.recordRefused(periodKind, periodStart, api.name());
            if (refused != null && refused == 1L) {
                log.warn("Map budget for {} reached its {} cap; saving mode until {}", api, periodKind, resumesAt);
                notifier.budgetReached(api, resumesAt);
            }
        } catch (DataAccessException exception) {
            log.warn("Map budget refusal not recorded for {}: {}", api, exception.getMessage());
        }
        return new Decision(false, resumesAt);
    }
}
