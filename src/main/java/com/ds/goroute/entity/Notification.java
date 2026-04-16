package com.ds.goroute.entity;

import com.ds.goroute.type.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private UUID id;
    private UUID userId;
    private UUID tripId;
    private NotificationType type;
    private String title;
    private String body;
    private String data; // JSON string
    private UUID actorId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
