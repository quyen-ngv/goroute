package com.ds.goroute.service.impl;

import com.ds.goroute.dto.websocket.WebSocketEvent;
import com.ds.goroute.entity.User;
import com.ds.goroute.mapper.UserMapper;
import com.ds.goroute.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketServiceImpl implements WebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final UserMapper userMapper;

    @Override
    public void broadcastToTrip(UUID tripId, String eventType, Map<String, Object> data, UUID actorId) {
        User actor = userMapper.selectById(actorId);
        
        WebSocketEvent event = WebSocketEvent.builder()
                .type(eventType)
                .data(data)
                .actor(WebSocketEvent.Actor.builder()
                        .id(actorId)
                        .fullName(actor != null ? actor.getFullName() : "Unknown")
                        .avatarUrl(actor != null ? actor.getAvatarUrl() : null)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();

        String destination = "/topic/trips/" + tripId;
        messagingTemplate.convertAndSend(destination, event);
        
        log.info("WebSocket event broadcasted: {} to trip: {}", eventType, tripId);
    }
}
