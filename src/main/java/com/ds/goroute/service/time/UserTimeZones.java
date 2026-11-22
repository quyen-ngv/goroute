package com.ds.goroute.service.time;

import com.ds.goroute.config.filter.ClientTimeZoneFilter;
import com.ds.goroute.mapper.UserMapper;
import com.ds.goroute.utils.AppZones;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * The zone a user's calendar day is counted in: daily quotas, "today" in answers.
 *
 * <p>Resolution order: the {@code X-Timezone} header of the current request, then the zone the
 * user's device last reported ({@code users.time_zone}), then {@link AppZones#VIETNAM}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTimeZones {

    private final UserMapper userMapper;

    private final Cache<UUID, Optional<ZoneId>> stored = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofHours(6))
            .build();

    /** The calling user's zone; request header first. */
    public ZoneId zoneFor(UUID userId) {
        ZoneId header = ClientTimeZoneFilter.currentZone();
        if (header != null) {
            return header;
        }
        if (userId == null) {
            return AppZones.VIETNAM;
        }
        return storedZone(userId).orElse(AppZones.VIETNAM);
    }

    /** The user's current calendar date. */
    public LocalDate today(UUID userId, Clock clock) {
        return LocalDate.now(clock.withZone(zoneFor(userId)));
    }

    /** Start of the user's current day as a UTC wall clock, the format TIMESTAMP columns hold. */
    public LocalDateTime startOfTodayUtc(UUID userId, Clock clock) {
        ZoneId zone = zoneFor(userId);
        return LocalDate.now(clock.withZone(zone)).atStartOfDay(zone)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Records the zone a device reported; writes only when it changed. */
    public void remember(UUID userId, ZoneId zone) {
        if (userId == null || zone == null) {
            return;
        }
        Optional<ZoneId> known = stored.getIfPresent(userId);
        if (known != null && known.isPresent() && known.get().equals(zone)) {
            return;
        }
        try {
            userMapper.updateTimeZone(userId, zone.getId());
            stored.put(userId, Optional.of(zone));
        } catch (RuntimeException e) {
            log.warn("Could not store time zone {} for user {}: {}", zone, userId, e.getMessage());
        }
    }

    private Optional<ZoneId> storedZone(UUID userId) {
        return stored.get(userId, id -> {
            try {
                return Optional.ofNullable(AppZones.parseOrNull(userMapper.selectTimeZone(id)));
            } catch (RuntimeException e) {
                log.warn("Could not read time zone for user {}: {}", id, e.getMessage());
                return Optional.empty();
            }
        });
    }
}
