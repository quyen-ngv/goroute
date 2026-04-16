package com.ds.goroute.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketEvent {
    private String type; // ACTIVITY_ADDED, ACTIVITY_UPDATED, etc.
    private Map<String, Object> data;
    private Actor actor;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Actor {
        private UUID id;
        private String fullName;
        private String avatarUrl;
    }
}
