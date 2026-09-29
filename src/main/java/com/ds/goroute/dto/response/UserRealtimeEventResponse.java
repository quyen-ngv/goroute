package com.ds.goroute.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned event sent to one person on {@code /topic/users/{userId}/events}.
 *
 * <p>Carries things that belong to the person rather than to a shared topic: a trip they were
 * removed from or that was deleted, a subscription the server refused, a new notification. Like
 * the trip events it is an invalidation signal, never a snapshot. Clients ignore unknown types.
 */
public record UserRealtimeEventResponse(
        UUID eventId,
        int version,
        String type,
        UUID userId,
        Instant occurredAt,
        Map<String, Object> payload) {

    public UserRealtimeEventResponse {
        if (version < 1) {
            throw new IllegalArgumentException("Realtime event version must be positive");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
