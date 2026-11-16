package com.ds.goroute.localevent.job;

import com.ds.goroute.localevent.service.LocalEventReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the reminders of saved local events that are due, every five minutes by default
 * ({@code goroute.jobs.local-event-reminders.delay}, milliseconds). Switched by
 * {@code EVENT/REMINDERS_ENABLED}. Single instance only, like every job in this application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalEventReminderJob {

    private final LocalEventReminderService reminderService;

    @Scheduled(fixedDelayString = "${goroute.jobs.local-event-reminders.delay:300000}")
    public void sendDueReminders() {
        try {
            int sent = reminderService.sendDue();
            if (sent > 0) {
                log.info("Sent {} local event reminders", sent);
            }
        } catch (RuntimeException exception) {
            log.warn("Local event reminder run failed: {}", exception.getMessage());
        }
    }
}
