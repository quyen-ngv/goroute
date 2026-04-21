package com.ds.goroute.service.notification.event;

import com.ds.goroute.type.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class TripEvent {
    private UUID tripId;
    private UUID actorId; // Người thực hiện hành động
    private NotificationType type;
    private Map<String, Object> metadata; // Dữ liệu bổ sung
    
    public abstract String getTitle();
    public abstract String getBody();
}
