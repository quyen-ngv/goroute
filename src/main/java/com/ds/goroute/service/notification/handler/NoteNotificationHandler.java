package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.strategy.AllMembersStrategy;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Handler cho Note-related notifications
 * - NOTE_ADDED
 * - NOTE_DELETED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NoteNotificationHandler implements NotificationEventHandler {

    private final NotificationService notificationService;
    private final AllMembersStrategy allMembersStrategy;

    @Override
    public void handle(TripEvent event) {
        log.info("ðŸ”µ NoteHandler: Handling event type={}", event.getType());

        List<UUID> recipients = allMembersStrategy.getRecipients(event);
        log.info("ðŸ“§ Found {} recipients", recipients.size());

        for (UUID recipientId : recipients) {
            notificationService.createNotification(recipientId, event);
        }

        log.info("âœ… Sent {} notifications for {}", recipients.size(), event.getType());
    }

    @Override
    public boolean supports(TripEvent event) {
        return event.getType() == NotificationType.NOTE_ADDED
            || event.getType() == NotificationType.NOTE_DELETED;
    }
}
