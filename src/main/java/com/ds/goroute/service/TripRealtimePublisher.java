package com.ds.goroute.service;

import com.ds.goroute.dto.response.TripRealtimeEventResponse;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Publishes trip changes only after the owning database transaction commits. */
@Service
@RequiredArgsConstructor
public class TripRealtimePublisher {
    private static final String TRIP_TOPIC_PREFIX = "/topic/trips/";
    private static final int EVENT_VERSION = 1;

    private final SimpMessagingTemplate messagingTemplate;

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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(type, tripId, entityId, actorId, payload);
                }
            });
            return;
        }
        send(type, tripId, entityId, actorId, payload);
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
        messagingTemplate.convertAndSend(TRIP_TOPIC_PREFIX + event.tripId(), event);
    }
}
