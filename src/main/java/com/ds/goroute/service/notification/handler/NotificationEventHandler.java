package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.event.TripEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Handler interface cho notification events
 * Mỗi handler chịu trách nhiệm xử lý một nhóm events cụ thể
 */
public interface NotificationEventHandler {
    /**
     * Xử lý event và gửi notification
     * @param event Event cần xử lý
     */
    void handle(TripEvent event);
    
    /**
     * Kiểm tra handler có hỗ trợ event này không
     * @param event Event cần kiểm tra
     * @return true nếu handler hỗ trợ event này
     */
    boolean supports(TripEvent event);

    /**
     * Creates one notification per recipient, each on its own: a recipient whose row or push
     * fails is logged and skipped, and the rest are still told.
     *
     * @return how many recipients were notified
     */
    default int notifyEach(NotificationService notificationService, List<UUID> recipients, TripEvent event) {
        int notified = 0;
        for (UUID recipientId : recipients) {
            try {
                notificationService.createNotification(recipientId, event);
                notified++;
            } catch (RuntimeException exception) {
                HandlerLog.LOG.error("Could not notify {} of {} on trip {}: {}",
                        recipientId, event.getType(), event.getTripId(), exception.getMessage(), exception);
            }
        }
        return notified;
    }

    /** Holder so the interface can log without a logger field of its own. */
    final class HandlerLog {
        private static final Logger LOG = LoggerFactory.getLogger(NotificationEventHandler.class);

        private HandlerLog() {
        }
    }
}
