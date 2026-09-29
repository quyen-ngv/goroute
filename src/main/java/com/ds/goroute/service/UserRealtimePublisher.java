package com.ds.goroute.service;

import com.ds.goroute.dto.response.UserRealtimeEventResponse;
import com.ds.goroute.type.UserRealtimeEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Publishes events to one person's own topic, only after the owning transaction commits. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserRealtimePublisher {
    public static final String USER_TOPIC_PREFIX = "/topic/users/";
    public static final String USER_EVENTS_SUFFIX = "/events";
    private static final int EVENT_VERSION = 1;

    private final SimpMessagingTemplate messagingTemplate;

    public void publishAfterCommit(UserRealtimeEventType type, UUID userId, Map<String, Object> payload) {
        if (userId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(type, userId, payload);
                }
            });
            return;
        }
        send(type, userId, payload);
    }

    public void publishAfterCommit(
            UserRealtimeEventType type, Collection<UUID> userIds, Map<String, Object> payload) {
        if (userIds == null) return;
        userIds.stream().distinct().forEach(userId -> publishAfterCommit(type, userId, payload));
    }

    /** Sends now. Only for callers that are already past any transaction, such as a socket interceptor. */
    public void send(UserRealtimeEventType type, UUID userId, Map<String, Object> payload) {
        if (userId == null) return;
        UserRealtimeEventResponse event = new UserRealtimeEventResponse(
                UUID.randomUUID(), EVENT_VERSION, type.wireValue(), userId, Instant.now(), payload);
        try {
            messagingTemplate.convertAndSend(USER_TOPIC_PREFIX + userId + USER_EVENTS_SUFFIX, event);
        } catch (RuntimeException exception) {
            // The write already committed; a lost realtime hint must not turn it into an error.
            log.warn("Could not publish {} to user {}: {}", type.wireValue(), userId, exception.getMessage());
        }
    }
}
