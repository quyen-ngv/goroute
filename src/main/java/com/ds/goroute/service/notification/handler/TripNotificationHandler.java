package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.event.TripUpdatedEvent;
import com.ds.goroute.service.notification.strategy.AllMembersStrategy;
import com.ds.goroute.service.notification.strategy.DirectRecipientsStrategy;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Handler cho Trip-related notifications
 * - TRIP_UPDATED
 * - TRIP_DELETED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TripNotificationHandler implements NotificationEventHandler {

    private final NotificationService notificationService;
    private final AllMembersStrategy allMembersStrategy;
    private final DirectRecipientsStrategy directRecipientsStrategy;

    @Override
    public void handle(TripEvent event) {
        log.info("ðŸ”µ TripHandler: Handling event type={}", event.getType());

        List<UUID> recipients = event.getMetadata() != null && event.getMetadata().containsKey("recipientIds")
                ? directRecipientsStrategy.getRecipients(event)
                : allMembersStrategy.getRecipients(event);
        log.info("ðŸ“§ Found {} recipients", recipients.size());

        for (UUID recipientId : recipients) {
            notificationService.createNotification(recipientId, event);
        }

        log.info("âœ… Sent {} notifications for {}", recipients.size(), event.getType());
    }

    @Override
    public boolean supports(TripEvent event) {
        return event.getType() == NotificationType.TRIP_UPDATED
            || event.getType() == NotificationType.TRIP_DELETED
            || event.getType() == NotificationType.MEMORY_ADDED
            || event.getType() == NotificationType.MEMORY_DELETED
            || event.getType() == NotificationType.TRIP_BOOK_UPDATED
            || event.getType() == NotificationType.TRIP_CLONED;
    }
}
