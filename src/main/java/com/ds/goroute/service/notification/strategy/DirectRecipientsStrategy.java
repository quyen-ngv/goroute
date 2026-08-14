package com.ds.goroute.service.notification.strategy;

import com.ds.goroute.service.notification.event.TripEvent;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class DirectRecipientsStrategy implements NotificationStrategy {
    @Override
    public List<UUID> getRecipients(TripEvent event) {
        Object value = event.getMetadata() == null ? null : event.getMetadata().get("recipientIds");
        if (!(value instanceof Collection<?> recipients)) {
            return List.of();
        }
        return recipients.stream()
                .map(this::toUuid)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !id.equals(event.getActorId()))
                .distinct()
                .toList();
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return value == null ? null : UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
