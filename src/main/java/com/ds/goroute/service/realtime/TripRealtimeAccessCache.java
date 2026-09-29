package com.ds.goroute.service.realtime;

import com.ds.goroute.service.TripAccessGuard;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Short-lived answers to "may this subscriber still read this trip?" for the socket's
 * per-message check.
 *
 * <p>Without it every trip event cost two queries per subscribed session, run on the thread that
 * published the event (the request that just committed). Membership rarely changes, and every
 * change that can revoke it publishes a {@code member.*}, {@code trip.deleted} or
 * {@code trip.accessRevoked} event, which evicts the whole trip here before the event fans out.
 * The TTL only bounds how long a change made some other way can take to be noticed.
 *
 * <p>This is a filter, not the authorization for any write: services keep calling
 * {@link TripAccessGuard} directly. A lookup that fails is not cached and the caller treats it as
 * a refusal, so the check stays fail-closed.
 */
@Service
public class TripRealtimeAccessCache {

    static final Duration TIME_TO_LIVE = Duration.ofSeconds(30);
    private static final long MAX_ENTRIES = 100_000;

    private final TripAccessGuard tripAccessGuard;
    private final Cache<TripUserKey, Boolean> readGrants = Caffeine.newBuilder()
            .expireAfterWrite(TIME_TO_LIVE)
            .maximumSize(MAX_ENTRIES)
            .build();

    public TripRealtimeAccessCache(TripAccessGuard tripAccessGuard) {
        this.tripAccessGuard = tripAccessGuard;
    }

    public boolean canRead(UUID tripId, UUID userId) {
        return readGrants.get(new TripUserKey(tripId, userId),
                key -> tripAccessGuard.canRead(key.tripId(), key.userId()));
    }

    /** Forgets every subscriber's answer for this trip; the next message re-reads membership. */
    public void evictTrip(UUID tripId) {
        if (tripId == null) {
            return;
        }
        readGrants.asMap().keySet().removeIf(key -> key.tripId().equals(tripId));
    }

    private record TripUserKey(UUID tripId, UUID userId) {
    }
}
