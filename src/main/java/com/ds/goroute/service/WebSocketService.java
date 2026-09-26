package com.ds.goroute.service;

import com.ds.goroute.dto.websocket.WebSocketEvent;

import java.util.Map;
import java.util.UUID;

public interface WebSocketService {
    void broadcastToTrip(UUID tripId, String eventType, Map<String, Object> data, UUID actorId);
    void broadcastToConversation(UUID conversationId, String eventType, Map<String, Object> data, UUID actorId);

    /**
     * Sends to one person wherever they are, rather than to a thread they have
     * open.
     *
     * <p>This is how an inbox learns that a conversation it is not showing has
     * moved: the thread's own topic only reaches whoever opened it, which is
     * exactly the people who did not need telling.
     */
    void broadcastToUser(UUID userId, String eventType, Map<String, Object> data, UUID actorId);
}
