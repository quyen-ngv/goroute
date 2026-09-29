package com.ds.goroute.service;

import com.ds.goroute.dto.response.TripRealtimeEventResponse;
import com.ds.goroute.service.realtime.TripRealtimeAccessCache;
import com.ds.goroute.type.TripAccessRevokedReason;
import com.ds.goroute.type.TripRealtimeEventType;
import com.ds.goroute.type.UserRealtimeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Publishes trip changes only after the owning database transaction commits. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TripRealtimePublisher {
    private static final String TRIP_TOPIC_PREFIX = "/topic/trips/";
    private static final int EVENT_VERSION = 1;

    /**
     * Events after which a subscriber's cached read grant for the trip may be wrong. Role and
     * guest changes do not revoke reading, but they are rare and re-reading is cheap.
     */
    private static final Set<TripRealtimeEventType> MEMBERSHIP_EVENTS = EnumSet.of(
            TripRealtimeEventType.TRIP_DELETED,
            TripRealtimeEventType.MEMBER_INVITED,
            TripRealtimeEventType.MEMBER_ACCEPTED,
            TripRealtimeEventType.MEMBER_DECLINED,
            TripRealtimeEventType.MEMBER_REMOVED,
            TripRealtimeEventType.MEMBER_ROLE_UPDATED,
            TripRealtimeEventType.MEMBER_GUEST_UPDATED);

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRealtimePublisher userRealtimePublisher;
    private final TripRealtimeAccessCache tripRealtimeAccessCache;

    public void publishAfterCommit(TripRealtimeEventType type, UUID tripId, UUID entityId) {
        publishAfterCommit(type, tripId, entityId, null, Map.of());
    }

    public void publishAfterCommit(
            TripRealtimeEventType type,
            UUID tripId,
            UUID entityId,
            UUID actorId) {
        publishAfterCommit(type, tripId, entityId, actorId, Map.of());
    }

    public void publishAfterCommit(
            TripRealtimeEventType type,
            UUID tripId,
            UUID entityId,
            Map<String, Object> payload) {
        publishAfterCommit(type, tripId, entityId, null, payload);
    }

    public void publishAfterCommit(
            TripRealtimeEventType type,
            UUID tripId,
            UUID entityId,
            UUID actorId,
            Map<String, Object> payload) {
        runAfterCommit(() -> send(type, tripId, entityId, actorId, payload));
    }

    /**
     * Tells each of these people, on their own topic, that the trip is gone for them.
     *
     * <p>The trip topic cannot carry this: its per-message check drops every event for somebody
     * who is no longer a member, and a soft-deleted trip has no members at all, so the very people
     * the news is about are the ones filtered out. Callers collect the user ids before the rows
     * that name them are deleted. Guests without an account have no user id and are skipped.
     */
    public void publishAccessRevokedAfterCommit(
            UUID tripId, Collection<UUID> userIds, TripAccessRevokedReason reason) {
        runAfterCommit(() -> tripRealtimeAccessCache.evictTrip(tripId));
        userRealtimePublisher.publishAfterCommit(
                UserRealtimeEventType.TRIP_ACCESS_REVOKED,
                userIds,
                Map.of("tripId", tripId, "reason", reason.wireValue()));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void send(
            TripRealtimeEventType type,
            UUID tripId,
            UUID entityId,
            UUID actorId,
            Map<String, Object> payload) {
        TripRealtimeEventResponse event = new TripRealtimeEventResponse(
                UUID.randomUUID(),
                EVENT_VERSION,
                type.wireValue(),
                tripId,
                entityId,
                actorId,
                Instant.now(),
                payload);
        try {
            // Before the fan-out, so every subscriber of this very event is checked afresh.
            if (MEMBERSHIP_EVENTS.contains(type)) {
                tripRealtimeAccessCache.evictTrip(tripId);
            }
            messagingTemplate.convertAndSend(TRIP_TOPIC_PREFIX + event.tripId(), event);
        } catch (RuntimeException exception) {
            // The write already committed; a lost invalidation hint must not turn it into a 500.
            log.warn("Could not publish {} for trip {}: {}", type.wireValue(), tripId, exception.getMessage());
        }
    }
}
