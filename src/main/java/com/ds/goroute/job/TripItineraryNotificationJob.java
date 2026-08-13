package com.ds.goroute.job;

import com.ds.goroute.service.notification.TripItineraryNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripItineraryNotificationJob {

    private final TripItineraryNotificationService notificationService;

    @Scheduled(fixedDelayString = "${goroute.jobs.itinerary-notifications-delay-ms:60000}")
    public void sendDueNotifications() {
        int delivered = notificationService.processDueNotifications(Instant.now());
        if (delivered > 0) {
            log.info("Delivered {} scheduled itinerary notifications", delivered);
        }
    }
}
