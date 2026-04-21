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
 * Handler cho Check-in notifications
 * - CHECKIN
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckinNotificationHandler implements NotificationEventHandler {
    
    private final NotificationService notificationService;
    private final AllMembersStrategy allMembersStrategy;
    
    @Override
    public void handle(TripEvent event) {
        log.info("🔵 CheckinHandler: Handling event type={}", event.getType());
        
        List<UUID> recipients = allMembersStrategy.getRecipients(event);
        log.info("📧 Found {} recipients", recipients.size());
        
        for (UUID recipientId : recipients) {
            notificationService.createNotification(
                recipientId,
                event.getTripId(),
                event.getType(),
                event.getTitle(),
                event.getBody(),
                event.getMetadata(),
                event.getActorId()
            );
        }
        
        log.info("✅ Sent {} notifications for {}", recipients.size(), event.getType());
    }
    
    @Override
    public boolean supports(TripEvent event) {
        return event.getType() == NotificationType.CHECKIN;
    }
}
