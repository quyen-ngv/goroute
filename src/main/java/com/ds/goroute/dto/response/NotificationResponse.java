package com.ds.goroute.dto.response;

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
public class NotificationResponse {
    private UUID id;
    private NotificationType type;
    private String title;
    private String body;
    private UUID tripId;
    private String deepLink;
    private UserResponse actor;
    private String data;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
