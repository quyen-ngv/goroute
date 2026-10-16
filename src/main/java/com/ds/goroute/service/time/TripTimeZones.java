package com.ds.goroute.service.time;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.thirdparty.weather.OpenMeteoClient;
import com.ds.goroute.utils.AppZones;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * The zone a trip's itinerary is written in: the destination's.
 *
 * <p>{@code trips.timezone} is filled in the background ({@link #resolvePending}) from the
 * destination point, so every way of creating a trip (manual, AI, copy) gets one without each
 * path knowing about it. Until then, a destination inside Vietnam's box reads as Vietnam time;
 * anything else reads as unknown and the app falls back to the device zone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripTimeZones {

    private final TripMapper tripMapper;
    private final OpenMeteoClient openMeteoClient;

    /** Points within ~5 km share a zone in practice; most trips go to the same few cities. */
    private final Cache<String, String> lookups = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofDays(30))
            .build();

    /** The stored zone, or a provisional one while the background lookup has not run. Null if unknown. */
    public static String zoneIdOf(Trip trip) {
        if (trip == null) {
            return null;
        }
        ZoneId stored = AppZones.parseOrNull(trip.getTimezone());
        if (stored != null) {
            return stored.getId();
        }
        return insideVietnamBox(trip.getDestinationLat(), trip.getDestinationLng())
                ? AppZones.VIETNAM.getId()
                : null;
    }

    /** {@link #zoneIdOf} with a Vietnam fallback, for server-side "today in this trip". */
    public static ZoneId zoneOf(Trip trip) {
        return AppZones.parseOr(zoneIdOf(trip), AppZones.VIETNAM);
    }

    /** Resolves up to {@code limit} trips that have a destination but no zone. Returns how many were set. */
    public int resolvePending(int limit) {
        List<Trip> pending = tripMapper.selectWithoutTimezone(limit);
        int resolved = 0;
        for (Trip trip : pending) {
            String zone = lookup(trip.getDestinationLat(), trip.getDestinationLng());
            if (zone == null) {
                continue;
            }
            resolved += tripMapper.updateTimezone(trip.getId(), zone,
                    trip.getDestinationLat(), trip.getDestinationLng());
        }
        return resolved;
    }

    String lookup(BigDecimal lat, BigDecimal lng) {
        String key = lat.setScale(2, RoundingMode.HALF_UP).toPlainString() + ","
                + lng.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String cached = lookups.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        try {
            ZoneId zone = AppZones.parseOrNull(openMeteoClient.lookupTimeZone(lat, lng));
            if (zone == null) {
                return null;
            }
            lookups.put(key, zone.getId());
            return zone.getId();
        } catch (RuntimeException e) {
            log.warn("Time zone lookup failed for {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    /**
     * Mainland Vietnam's box. It also clips eastern Thailand, Laos and Cambodia, all UTC+7 without
     * DST, and a strip of southern China (UTC+8); the stored lookup corrects those within minutes.
     */
    static boolean insideVietnamBox(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            return false;
        }
        double la = lat.doubleValue();
        double lo = lng.doubleValue();
        return la >= 8.0 && la <= 23.5 && lo >= 102.0 && lo <= 110.0;
    }
}
