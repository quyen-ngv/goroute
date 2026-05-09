package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.strategy.AllMembersStrategy;
import com.ds.goroute.service.notification.strategy.CustomRecipientStrategy;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Handler cho Member-related notifications
 * - MEMBER_ADDED
 * - MEMBER_REMOVED (custom logic)
 * - GUEST_LINKED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberNotificationHandler implements NotificationEventHandler {
    
    private final NotificationService notificationService;
    private final AllMembersStrategy allMembersStrategy;
    private final CustomRecipientStrategy customRecipientStrategy;
    
    @Override
    public void handle(TripEvent event) {
        log.info("🔵 MemberHandler: Handling event type={}", event.getType());
        
        List<UUID> recipients;
        
        // MEMBER_REMOVED cần custom logic
        if (event.getType() == NotificationType.MEMBER_REMOVED) {
            recipients = customRecipientStrategy.getRecipients(event);
        } else {
            recipients = allMembersStrategy.getRecipients(event);
        }
        
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
        return event.getType() == NotificationType.MEMBER_ADDED
            || event.getType() == NotificationType.MEMBER_REMOVED
            || event.getType() == NotificationType.GUEST_LINKED;
    }
}
