package com.ds.goroute.service.notification.handler;

import com.ds.goroute.service.notification.event.TripEvent;

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
}
