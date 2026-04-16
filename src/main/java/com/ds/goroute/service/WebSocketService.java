package com.ds.goroute.service;

import com.ds.goroute.dto.websocket.WebSocketEvent;

import java.util.Map;
import java.util.UUID;

public interface WebSocketService {
    void broadcastToTrip(UUID tripId, String eventType, Map<String, Object> data, UUID actorId);
}
