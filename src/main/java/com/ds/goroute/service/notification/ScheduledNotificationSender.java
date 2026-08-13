package com.ds.goroute.service.notification;

import com.ds.goroute.mapper.ScheduledNotificationDeliveryMapper;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ScheduledNotificationSender {

    private final ScheduledNotificationDeliveryMapper deliveryMapper;
    private final NotificationService notificationService;

    @Transactional
    public boolean sendOnce(UUID recipientId,
                            UUID tripId,
                            NotificationType type,
                            String eventKey,
                            ZonedDateTime scheduledAt,
                            Map<String, Object> data) {
        LocalDateTime scheduledForUtc = scheduledAt
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        int claimed = deliveryMapper.claim(
                UUID.randomUUID(),
                recipientId,
                tripId,
                eventKey,
                scheduledForUtc
        );
        if (claimed == 0) {
            return false;
        }

        notificationService.createNotification(
                recipientId,
                tripId,
                type,
                null,
                null,
                data,
                null
        );
        return true;
    }
}
