package com.ds.goroute.service.notification.strategy;

import com.ds.goroute.service.notification.event.TripEvent;

import java.util.List;
import java.util.UUID;

/**
 * Strategy pattern để xác định recipients cho notification
 */
public interface NotificationStrategy {
    /**
     * Lấy danh sách user IDs cần nhận notification
     * @param event Event trigger notification
     * @return List of user IDs
     */
    List<UUID> getRecipients(TripEvent event);
}
