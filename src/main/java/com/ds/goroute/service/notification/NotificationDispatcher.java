package com.ds.goroute.service.notification;

import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.service.notification.handler.NotificationEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Central dispatcher cho notification events
 * Tìm handler phù hợp và dispatch event
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {
    
    private final List<NotificationEventHandler> handlers;
    
    /**
     * Dispatch event đến handler phù hợp
     * Chạy async để không block main thread
     */
    @Async
    public void dispatch(TripEvent event) {
        log.info("Dispatching notification event: type={}, tripId={}", 
                event.getType(), event.getTripId());
        
        handlers.stream()
                .filter(handler -> handler.supports(event))
                .forEach(handler -> {
                    try {
                        handler.handle(event);
                    } catch (Exception e) {
                        log.error("Error handling notification event: {}", e.getMessage(), e);
                    }
                });
    }
}
