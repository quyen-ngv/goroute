package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.strategy.ExpenseMembersStrategy;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Handler cho Expense-related notifications
 * - EXPENSE_ADDED
 * - EXPENSE_UPDATED
 * - EXPENSE_DELETED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpenseNotificationHandler implements NotificationEventHandler {

    private final NotificationService notificationService;
    private final ExpenseMembersStrategy expenseMembersStrategy;

    @Override
    public void handle(TripEvent event) {
        log.info("ðŸ”µ ExpenseHandler: Handling event type={}", event.getType());

        List<UUID> recipients = expenseMembersStrategy.getRecipients(event);
        log.info("ðŸ“§ Found {} recipients (expense members)", recipients.size());

        for (UUID recipientId : recipients) {
            notificationService.createNotification(recipientId, event);
        }

        log.info("âœ… Sent {} notifications for {}", recipients.size(), event.getType());
    }

    @Override
    public boolean supports(TripEvent event) {
        return event.getType() == NotificationType.EXPENSE_ADDED
            || event.getType() == NotificationType.EXPENSE_UPDATED
            || event.getType() == NotificationType.EXPENSE_DELETED;
    }
}
