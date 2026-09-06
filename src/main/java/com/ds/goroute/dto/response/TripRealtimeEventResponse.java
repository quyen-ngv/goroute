package com.ds.goroute.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned, deliberately small notification that a trip resource changed.
 *
 * <p>The client uses it to reconcile one affected provider with the authoritative HTTP resource;
 * it must never be treated as a complete trip snapshot.
 */
public record TripRealtimeEventResponse(
        UUID eventId,
        int version,
        String type,
        UUID tripId,
        UUID entityId,
        UUID actorId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public TripRealtimeEventResponse {
        if (version < 1) {
            throw new IllegalArgumentException("Realtime event version must be positive");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
