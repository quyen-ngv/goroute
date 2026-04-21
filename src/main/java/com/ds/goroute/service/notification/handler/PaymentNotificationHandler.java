package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.strategy.AllMembersStrategy;
import com.ds.goroute.service.notification.strategy.ExpenseMembersStrategy;
import com.ds.goroute.service.notification.strategy.PayeeOnlyStrategy;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Handler cho Payment-related notifications
 * - PAYMENT_MARKED (1 split) → PayeeOnlyStrategy
 * - PAYMENT_ALL_MARKED (1 expense) → ExpenseMembersStrategy
 * - PAYMENT_TRIP_MARKED (toàn trip) → AllMembersStrategy
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationHandler implements NotificationEventHandler {
    
    private final NotificationService notificationService;
    private final PayeeOnlyStrategy payeeOnlyStrategy;
    private final ExpenseMembersStrategy expenseMembersStrategy;
    private final AllMembersStrategy allMembersStrategy;
    
    @Override
    public void handle(TripEvent event) {
        log.info("🔵 PaymentHandler: Handling event type={}", event.getType());
        
        List<UUID> recipients;
        
        // Chọn strategy dựa vào type
        if (event.getType() == NotificationType.PAYMENT_MARKED) {
            recipients = payeeOnlyStrategy.getRecipients(event);
        } else if (event.getType() == NotificationType.PAYMENT_ALL_MARKED) {
            recipients = expenseMembersStrategy.getRecipients(event);
        } else { // PAYMENT_TRIP_MARKED
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
        return event.getType() == NotificationType.PAYMENT_MARKED
            || event.getType() == NotificationType.PAYMENT_ALL_MARKED
            || event.getType() == NotificationType.PAYMENT_TRIP_MARKED;
    }
}
