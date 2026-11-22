package com.ds.goroute.localevent.service;

import com.ds.goroute.entity.User;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.persistence.LocalEventSaveRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.LocalEventStatus;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reminders for saved local events (epic 17, V285). A person who saved an event (going saves
 * it) is told when it is near, in the event's own time zone:
 *
 * <pre>
 * one-day event (36 h or shorter)
 *   DAY_BEFORE      the day before it starts, between 09:00 and 21:00
 *   STARTING_SOON   within the two hours before it starts
 * long-running event (an exhibition, a fair)
 *   OPENING_TODAY   on its first day, between 09:00 and 21:00
 *   ENDING_SOON     one to three days before its last day, between 09:00 and 21:00,
 *                   only to people with no check-in at it
 * any event
 *   STATUS:...      once per status change to CANCELLED, POSTPONED or RESCHEDULED
 * </pre>
 *
 * <p>Each (event, person, kind) is claimed in {@code local_event_reminders} before it is sent, so
 * the job can run every few minutes and a restart never repeats a reminder. A cancelled or
 * postponed event sends no time reminders.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalEventReminderService {

    /** An event longer than this is "long-running": it gets opening / closing reminders. */
    static final Duration LONG_RUNNING = Duration.ofHours(36);

    /** How long before the start the "starting soon" reminder goes out. */
    static final Duration STARTING_SOON_LEAD = Duration.ofHours(2);

    /** The "ending soon" reminder goes out this many days, at most, before the last day. */
    static final int ENDING_SOON_DAYS = 3;

    /** Day reminders are only sent between these local hours. */
    static final int DAY_START_HOUR = 9;
    static final int DAY_END_HOUR = 21;

    /** Events looked at per run, and people reminded per event and kind per run. */
    static final int EVENT_BATCH = 200;
    static final int RECIPIENT_BATCH = 500;

    static final String KIND_DAY_BEFORE = "DAY_BEFORE";
    static final String KIND_STARTING_SOON = "STARTING_SOON";
    static final String KIND_OPENING_TODAY = "OPENING_TODAY";
    static final String KIND_ENDING_SOON = "ENDING_SOON";
    static final String KIND_STATUS_PREFIX = "STATUS:";

    private static final String DEEP_LINK_PREFIX = "/events/";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter STATUS_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final LocalEventRepository events;
    private final LocalEventSaveRepository saves;
    private final UserRepository users;
    private final NotificationTemplateRenderer templateRenderer;
    private final NotificationService notificationService;
    private final BusinessConfigService config;

    /** Replaced in tests. */
    Clock clock = Clock.systemUTC();

    /** One reminder that is due for an event right now. */
    record Due(String kind, NotificationType type, boolean skipCheckedIn) {
    }

    /** One job run. Returns how many reminders were sent. */
    public int sendDue() {
        if (!config.getBoolean(BusinessConfigKey.EVENT_REMINDERS_ENABLED)) {
            return 0;
        }
        LocalDateTime nowUtc = LocalDateTime.now(clock);
        List<LocalEvent> candidates = events.findReminderCandidates(nowUtc,
                nowUtc.plusDays(2), nowUtc.plusDays(ENDING_SOON_DAYS + 1L), EVENT_BATCH);
        int sent = 0;
        for (LocalEvent event : candidates) {
            for (Due due : dueReminders(event, nowUtc)) {
                sent += send(event, due, nowUtc);
            }
        }
        return sent;
    }

    /** The reminders due for {@code event} at {@code nowUtc}; times are compared in the event's zone. */
    static List<Due> dueReminders(LocalEvent event, LocalDateTime nowUtc) {
        List<Due> due = new ArrayList<>();
        if (event.getStartAt() == null || event.getEndAt() == null) {
            return due;
        }
        String status = event.getEventStatus();
        if (LocalEventStatus.CANCELLED.name().equals(status)) {
            due.add(new Due(statusKind(event), NotificationType.LOCAL_EVENT_CANCELLED, false));
            return due;
        }
        if (LocalEventStatus.POSTPONED.name().equals(status)) {
            due.add(new Due(statusKind(event), NotificationType.LOCAL_EVENT_POSTPONED, false));
            return due;
        }
        if (LocalEventStatus.RESCHEDULED.name().equals(status)) {
            due.add(new Due(statusKind(event), NotificationType.LOCAL_EVENT_RESCHEDULED, false));
        }

        ZoneId zone = zoneOf(event);
        ZonedDateTime now = nowUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
        ZonedDateTime start = event.getStartAt().atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
        ZonedDateTime end = event.getEndAt().atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
        LocalDate today = now.toLocalDate();
        boolean daytime = now.getHour() >= DAY_START_HOUR && now.getHour() < DAY_END_HOUR;
        boolean longRunning = Duration.between(start, end).compareTo(LONG_RUNNING) > 0;

        if (!longRunning) {
            if (daytime && today.equals(start.toLocalDate().minusDays(1))) {
                due.add(new Due(KIND_DAY_BEFORE, NotificationType.LOCAL_EVENT_TOMORROW, false));
            }
            if (now.isBefore(start) && !now.isBefore(start.minus(STARTING_SOON_LEAD))) {
                due.add(new Due(KIND_STARTING_SOON, NotificationType.LOCAL_EVENT_STARTING_SOON, false));
            }
            return due;
        }
        if (daytime && today.equals(start.toLocalDate())) {
            due.add(new Due(KIND_OPENING_TODAY, NotificationType.LOCAL_EVENT_OPENING_TODAY, false));
        }
        long daysLeft = ChronoUnit.DAYS.between(today, end.toLocalDate());
        if (daytime && now.isAfter(start) && daysLeft >= 1 && daysLeft <= ENDING_SOON_DAYS) {
            due.add(new Due(KIND_ENDING_SOON, NotificationType.LOCAL_EVENT_ENDING_SOON, true));
        }
        return due;
    }

    private int send(LocalEvent event, Due due, LocalDateTime nowUtc) {
        List<UUID> recipients = saves.findSaversWithoutReminder(
                event.getId(), due.kind(), due.skipCheckedIn(), RECIPIENT_BATCH);
        int sent = 0;
        for (UUID recipientId : recipients) {
            // Claimed first: a failed send is not retried, a repeated reminder would be worse.
            if (!saves.claimReminder(event.getId(), recipientId, due.kind(), nowUtc)) {
                continue;
            }
            try {
                deliver(recipientId, due.type(), event);
                sent++;
            } catch (RuntimeException exception) {
                log.warn("Could not send {} for event {} to {}: {}", due.type(), event.getId(), recipientId,
                        exception.getMessage());
            }
        }
        return sent;
    }

    private void deliver(UUID recipientId, NotificationType type, LocalEvent event) {
        String language = users.findById(recipientId).map(User::getLanguage).orElse(null);
        ZoneId zone = zoneOf(event);
        ZonedDateTime start = event.getStartAt().atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
        ZonedDateTime end = event.getEndAt().atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", event.getId().toString());
        data.put("eventTitle", event.getTitle());
        data.put("startTime", TIME.format(start));
        data.put("startDate", DATE.format(start));
        data.put("endDate", DATE.format(end));
        data.put("deepLink", DEEP_LINK_PREFIX + event.getId());
        NotificationMessage message = templateRenderer.render(type, data, language);
        // The app draws a notification from its type; a version that does not know these types
        // falls back to the title and body carried in the data, so every installed version
        // shows the reminder in words and opens the event from its deep link.
        data.put("title", message.title());
        data.put("body", message.body());
        notificationService.createNotification(recipientId, null, type, message.title(), message.body(), data, null);
    }

    /**
     * A status change is announced once per start time, so a second reschedule is announced
     * again. The start is written as UTC {@code yyyyMMddHHmm}: V285 pre-fills this key for the
     * status changes that happened before reminders existed, in the same format.
     */
    static String statusKind(LocalEvent event) {
        return KIND_STATUS_PREFIX + event.getEventStatus() + ":" + STATUS_STAMP.format(event.getStartAt());
    }

    private static ZoneId zoneOf(LocalEvent event) {
        try {
            return event.getTimezone() == null || event.getTimezone().isBlank()
                    ? LocalEventTimes.VIETNAM : ZoneId.of(event.getTimezone());
        } catch (RuntimeException exception) {
            return LocalEventTimes.VIETNAM;
        }
    }
}
