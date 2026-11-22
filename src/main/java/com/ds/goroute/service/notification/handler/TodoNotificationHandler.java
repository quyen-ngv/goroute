package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.strategy.DirectRecipientsStrategy;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Todo lists in trip notes (epic 04 §2.4): TODO_ASSIGNED, to the people the service
 * named in {@code recipientIds}, never the actor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodoNotificationHandler implements NotificationEventHandler {

    private final NotificationService notificationService;
    private final DirectRecipientsStrategy directRecipientsStrategy;

    @Override
    public void handle(TripEvent event) {
        List<UUID> recipients = directRecipientsStrategy.getRecipients(event);
        int notified = notifyEach(notificationService, recipients, event);
        log.info("Sent {} notifications for {}", notified, event.getType());
    }

    @Override
    public boolean supports(TripEvent event) {
        return event.getType() == NotificationType.TODO_ASSIGNED;
    }
}
