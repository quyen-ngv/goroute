package com.ds.goroute.service.notification;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.TripStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripItineraryNotificationService {

    static final ZoneId DEFAULT_TRIP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalTime DEFAULT_TRIP_START_TIME = LocalTime.of(9, 0);
    static final LocalTime DEFAULT_TRIP_END_TIME = LocalTime.of(20, 0);
    static final Duration DELIVERY_GRACE = Duration.ofMinutes(10);
    static final Duration UPCOMING_LEAD = Duration.ofMinutes(15);
    static final Duration TRANSPORT_PREPARATION_LEAD = Duration.ofMinutes(30);
    static final Duration BOOKING_PREPARATION_LEAD = Duration.ofHours(2);
    static final Duration SUMMARY_DELAY = Duration.ofMinutes(5);

    private static final List<TripLeadReminder> LEAD_REMINDERS = List.of(
            new TripLeadReminder(NotificationType.TRIP_STARTS_IN_ONE_WEEK, Duration.ofDays(7)),
            new TripLeadReminder(NotificationType.TRIP_STARTS_IN_THREE_DAYS, Duration.ofDays(3)),
            new TripLeadReminder(NotificationType.TRIP_STARTS_IN_TWO_DAYS, Duration.ofDays(2)),
            new TripLeadReminder(NotificationType.TRIP_STARTS_IN_ONE_DAY, Duration.ofDays(1)),
            new TripLeadReminder(NotificationType.TRIP_STARTS_IN_TWO_HOURS, Duration.ofHours(2))
    );

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ActivityRepository activityRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final UserRepository userRepository;
    private final ScheduledNotificationSender notificationSender;

    public int processDueNotifications(Instant now) {
        LocalDate utcDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        List<Trip> candidates = tripRepository.findNotificationCandidates(
                utcDate.minusDays(2),
                utcDate.plusDays(9)
        );

        int delivered = 0;
        for (Trip trip : candidates) {
            try {
                delivered += processTrip(trip, now);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to process itinerary notifications for trip {}",
                        trip.getId(),
                        exception
                );
            }
        }
        return delivered;
    }

    int processTrip(Trip trip, Instant now) {
        if (trip.getStatus() == TripStatus.CANCELLED) {
            return 0;
        }

        ZoneId zone = resolveZone(trip.getTimezone());
        List<Activity> activities = activityRepository.findByTripId(trip.getId());
        List<ScheduledItem> items = buildScheduledItems(trip, zone, activities);
        ZonedDateTime tripStart = resolveTripStart(trip, items, zone);
        ZonedDateTime tripEnd = resolveTripEnd(trip, items, zone);
        Set<UUID> recipients = resolveRecipients(trip);
        if (recipients.isEmpty()) {
            return 0;
        }

        int delivered = 0;
        for (TripLeadReminder reminder : LEAD_REMINDERS) {
            ZonedDateTime reminderAt = tripStart.minus(reminder.lead());
            if (reminder.type() == NotificationType.TRIP_STARTS_IN_TWO_HOURS
                    && hasItemPreparationAt(items, reminderAt)) {
                continue;
            }
            delivered += dispatchToRecipients(
                    trip,
                    recipients,
                    reminder.type(),
                    reminderAt,
                    now,
                    tripData(trip, "/trip/" + trip.getId()),
                    null
            );
        }

        delivered += dispatchToRecipients(
                trip,
                recipients,
                NotificationType.TRIP_STARTED,
                tripStart,
                now,
                tripData(trip, "/trip/" + trip.getId()),
                null
        );

        for (int index = 0; index < items.size(); index++) {
            ScheduledItem item = items.get(index);
            ScheduledItem nextItem = index + 1 < items.size() ? items.get(index + 1) : null;
            ZonedDateTime completedAt = resolveItemEnd(item, nextItem, tripEnd);
            Map<String, Object> itemData = itemData(trip, item, nextItem);

            Duration preparationLead = preparationLead(item.activity());
            if (preparationLead != null) {
                itemData.put("preparationLeadMinutes", preparationLead.toMinutes());
                delivered += dispatchToRecipients(
                        trip,
                        recipients,
                        NotificationType.ITINERARY_ITEM_PREPARATION,
                        item.startsAt().minus(preparationLead),
                        now,
                        itemData,
                        item.activity().getId()
                );
            }

            delivered += dispatchToRecipients(
                    trip,
                    recipients,
                    NotificationType.ITINERARY_ITEM_UPCOMING,
                    item.startsAt().minus(UPCOMING_LEAD),
                    now,
                    itemData,
                    item.activity().getId()
            );
            if (!isTransport(item.activity())) {
                delivered += dispatchToRecipients(
                        trip,
                        recipients,
                        NotificationType.ITINERARY_ITEM_COMPLETED,
                        completedAt,
                        now,
                        itemData,
                        item.activity().getId()
                );
            }
        }

        delivered += dispatchToRecipients(
                trip,
                recipients,
                NotificationType.TRIP_ENDED,
                tripEnd,
                now,
                tripData(trip, "/trip/" + trip.getId()),
                null
        );

        ZonedDateTime summaryAt = tripEnd.plus(SUMMARY_DELAY);
        if (isDue(summaryAt, now)) {
            TripTotals totals = calculateTripTotals(trip, activities);
            for (UUID recipientId : recipients) {
                Map<String, Object> data = summaryData(trip, recipientId, totals);
                if (sendOnce(
                        trip,
                        recipientId,
                        NotificationType.TRIP_SUMMARY,
                        summaryAt,
                        data,
                        null
                )) {
                    delivered++;
                }
            }
        }

        return delivered;
    }

    private boolean hasItemPreparationAt(List<ScheduledItem> items, ZonedDateTime scheduledAt) {
        Instant target = scheduledAt.toInstant();
        return items.stream().anyMatch(item -> {
            Duration lead = preparationLead(item.activity());
            return lead != null && item.startsAt().minus(lead).toInstant().equals(target);
        });
    }

    private int dispatchToRecipients(Trip trip,
                                     Set<UUID> recipients,
                                     NotificationType type,
                                     ZonedDateTime scheduledAt,
                                     Instant now,
                                     Map<String, Object> data,
                                     UUID activityId) {
        if (!isDue(scheduledAt, now)) {
            return 0;
        }

        int delivered = 0;
        for (UUID recipientId : recipients) {
            if (sendOnce(trip, recipientId, type, scheduledAt, data, activityId)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean sendOnce(Trip trip,
                             UUID recipientId,
                             NotificationType type,
                             ZonedDateTime scheduledAt,
                             Map<String, Object> data,
                             UUID activityId) {
        String subject = activityId != null ? activityId.toString() : trip.getId().toString();
        String eventKey = String.join(
                ":",
                trip.getId().toString(),
                type.name(),
                subject,
                String.valueOf(scheduledAt.toInstant().getEpochSecond())
        );
        return notificationSender.sendOnce(
                recipientId,
                trip.getId(),
                type,
                eventKey,
                scheduledAt,
                data
        );
    }

    private boolean isDue(ZonedDateTime scheduledAt, Instant now) {
        Instant scheduled = scheduledAt.toInstant();
        return !scheduled.isAfter(now) && !scheduled.isBefore(now.minus(DELIVERY_GRACE));
    }

    private Set<UUID> resolveRecipients(Trip trip) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (trip.getOwnerId() != null) {
            recipients.add(trip.getOwnerId());
        }
        for (TripMember member : tripMemberRepository.findByTripId(trip.getId())) {
            if (member.getUserId() != null && member.getStatus() == MemberStatus.ACCEPTED) {
                recipients.add(member.getUserId());
            }
        }
        return recipients;
    }

    private List<ScheduledItem> buildScheduledItems(Trip trip,
                                                    ZoneId zone,
                                                    List<Activity> activities) {
        List<ScheduledItem> items = new ArrayList<>();
        for (Activity activity : activities) {
            if (!isNotifiableItineraryItem(activity)) {
                continue;
            }
            ZonedDateTime startsAt = activityStart(trip, activity, zone);
            if (startsAt == null) {
                continue;
            }
            items.add(new ScheduledItem(activity, startsAt, explicitActivityEnd(trip, activity, zone)));
        }
        items.sort(Comparator.comparing(ScheduledItem::startsAt));
        return items;
    }

    private boolean isNotifiableItineraryItem(Activity activity) {
        return activity.getDayNumber() != null
                && activity.getStartTime() != null
                && !Boolean.TRUE.equals(activity.getIsStartingPoint());
    }

    private Duration preparationLead(Activity activity) {
        if (activity.getBookingId() != null) {
            return BOOKING_PREPARATION_LEAD;
        }
        if (isTransport(activity)) {
            return TRANSPORT_PREPARATION_LEAD;
        }
        return null;
    }

    private boolean isTransport(Activity activity) {
        return "transport".equalsIgnoreCase(activity.getCategory());
    }

    private ZonedDateTime activityStart(Trip trip, Activity activity, ZoneId zone) {
        if (activity.getStartingPointDate() != null) {
            return activity.getStartingPointDate().atZone(zone);
        }
        if (trip.getStartDate() == null || activity.getDayNumber() == null || activity.getStartTime() == null) {
            return null;
        }
        LocalDate date = trip.getStartDate().plusDays(Math.max(0, activity.getDayNumber() - 1L));
        return ZonedDateTime.of(date, activity.getStartTime(), zone);
    }

    private ZonedDateTime explicitActivityEnd(Trip trip, Activity activity, ZoneId zone) {
        if (trip.getStartDate() == null || activity.getEndTime() == null) {
            return null;
        }
        int endDayNumber = activity.getEndDayNumber() != null
                ? activity.getEndDayNumber()
                : activity.getDayNumber();
        LocalDate date = trip.getStartDate().plusDays(Math.max(0, endDayNumber - 1L));
        ZonedDateTime end = ZonedDateTime.of(date, activity.getEndTime(), zone);
        ZonedDateTime start = activityStart(trip, activity, zone);
        if (start != null && !end.isAfter(start) && activity.getEndDayNumber() == null) {
            end = end.plusDays(1);
        }
        return end;
    }

    private ZonedDateTime resolveTripStart(Trip trip, List<ScheduledItem> items, ZoneId zone) {
        if (trip.getStartingPointTime() != null) {
            return trip.getStartingPointTime().atZone(zone);
        }
        if (!items.isEmpty() && items.get(0).startsAt().toLocalDate().equals(trip.getStartDate())) {
            return items.get(0).startsAt();
        }
        return ZonedDateTime.of(trip.getStartDate(), DEFAULT_TRIP_START_TIME, zone);
    }

    private ZonedDateTime resolveTripEnd(Trip trip, List<ScheduledItem> items, ZoneId zone) {
        ZonedDateTime end = ZonedDateTime.of(trip.getEndDate(), DEFAULT_TRIP_END_TIME, zone);
        for (ScheduledItem item : items) {
            if (item.explicitEndsAt() != null && item.explicitEndsAt().isAfter(end)) {
                end = item.explicitEndsAt();
            }
        }
        return end;
    }

    private ZonedDateTime resolveItemEnd(ScheduledItem item,
                                         ScheduledItem nextItem,
                                         ZonedDateTime tripEnd) {
        ZonedDateTime end = item.explicitEndsAt();
        if (end == null && nextItem != null) {
            end = nextItem.startsAt();
        }
        if (end == null) {
            end = item.startsAt().plusHours(1);
        }
        if (end.isAfter(tripEnd)) {
            return tripEnd;
        }
        return end;
    }

    private Map<String, Object> tripData(Trip trip, String deepLink) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tripId", trip.getId().toString());
        data.put("tripName", trip.getName());
        data.put("deepLink", deepLink);
        return data;
    }

    private Map<String, Object> itemData(Trip trip,
                                         ScheduledItem item,
                                         ScheduledItem nextItem) {
        Activity activity = item.activity();
        Map<String, Object> data = tripData(
                trip,
                "/trip/" + trip.getId() + "/activities/" + activity.getId()
        );
        data.put("activityId", activity.getId().toString());
        data.put("itemName", activity.getName());
        data.put("itemKind", itemKind(activity));
        if (activity.getTransportMode() != null) {
            data.put("transportMode", activity.getTransportMode().name());
        }
        if (activity.getAddress() != null && !activity.getAddress().isBlank()) {
            data.put("startAddress", activity.getAddress());
        }
        if (activity.getEndAddress() != null && !activity.getEndAddress().isBlank()) {
            data.put("endAddress", activity.getEndAddress());
        }
        if (activity.getBookingId() != null) {
            data.put("bookingId", activity.getBookingId().toString());
            if (activity.getBookingSource() != null && !activity.getBookingSource().isBlank()) {
                data.put("bookingSource", activity.getBookingSource());
            }
        }
        if (nextItem != null) {
            data.put("nextItemName", nextItem.activity().getName());
            data.put("nextItemKind", itemKind(nextItem.activity()));
        }
        return data;
    }

    private String itemKind(Activity activity) {
        if (activity.getBookingId() != null) {
            return "booking";
        }
        if (isTransport(activity)) {
            return "transport";
        }
        if (activity.getPlaceId() != null
                || activity.getCustomPlaceId() != null
                || activity.getPlaceRefId() != null
                || (activity.getLat() != null && activity.getLng() != null)) {
            return "place";
        }
        return "activity";
    }

    private TripTotals calculateTripTotals(Trip trip, List<Activity> activities) {
        List<Expense> expenses = expenseRepository.findByTripId(trip.getId());
        BigDecimal totalExpense = expenses.stream()
                .map(this::expenseInTripCurrency)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long placesCount = activities.stream()
                .filter(activity -> !Boolean.TRUE.equals(activity.getIsStartingPoint()))
                .filter(activity -> !"transport".equalsIgnoreCase(activity.getCategory()))
                .filter(activity -> "place".equals(itemKind(activity)) || "booking".equals(itemKind(activity)))
                .count();
        return new TripTotals(expenses, totalExpense, placesCount);
    }

    private Map<String, Object> summaryData(Trip trip,
                                            UUID recipientId,
                                            TripTotals totals) {
        BigDecimal outstanding = BigDecimal.ZERO;
        Set<String> payeeNames = new LinkedHashSet<>();
        for (Expense expense : totals.expenses()) {
            if (isRecipientPayer(expense, recipientId)) {
                continue;
            }
            for (ExpenseSplit split : expenseSplitRepository.findByExpenseId(expense.getId())) {
                if (!recipientId.equals(split.getUserId()) || Boolean.TRUE.equals(split.getIsSettled())) {
                    continue;
                }
                outstanding = outstanding.add(splitInTripCurrency(expense, split));
                payeeNames.add(resolvePayeeName(expense));
            }
        }

        Map<String, Object> data = tripData(trip, "/trip/" + trip.getId() + "/budget");
        data.put("totalExpense", decimalText(totals.totalExpense()));
        data.put("currency", trip.getCurrency() != null ? trip.getCurrency() : "");
        data.put("placesCount", totals.placesCount());
        data.put("outstandingAmount", decimalText(outstanding));
        data.put("hasOutstandingDebt", outstanding.compareTo(BigDecimal.ZERO) > 0);
        data.put("payeeNames", String.join(", ", payeeNames));
        return data;
    }

    private boolean isRecipientPayer(Expense expense, UUID recipientId) {
        return recipientId.equals(expense.getPaidBy());
    }

    private BigDecimal expenseInTripCurrency(Expense expense) {
        if (expense.getAmountInTripCurrency() != null) {
            return expense.getAmountInTripCurrency();
        }
        return expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO;
    }

    private BigDecimal splitInTripCurrency(Expense expense, ExpenseSplit split) {
        if (split.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        if (expense.getAmount() == null
                || expense.getAmount().compareTo(BigDecimal.ZERO) == 0
                || expense.getAmountInTripCurrency() == null) {
            return split.getAmount();
        }
        return split.getAmount()
                .multiply(expense.getAmountInTripCurrency())
                .divide(expense.getAmount(), 2, RoundingMode.HALF_UP);
    }

    private String resolvePayeeName(Expense expense) {
        if (expense.getPaidBy() != null) {
            return userRepository.findById(expense.getPaidBy())
                    .map(this::displayName)
                    .orElse("");
        }
        return expense.getPaidByGuestName() != null ? expense.getPaidByGuestName() : "";
    }

    private String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return user.getEmail() != null ? user.getEmail() : "";
    }

    private String decimalText(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TRIP_ZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException exception) {
            log.warn("Invalid timezone '{}' for trip notification; using {}", timezone, DEFAULT_TRIP_ZONE);
            return DEFAULT_TRIP_ZONE;
        }
    }

    record ScheduledItem(Activity activity,
                         ZonedDateTime startsAt,
                         ZonedDateTime explicitEndsAt) {
    }

    private record TripLeadReminder(NotificationType type, Duration lead) {
    }

    private record TripTotals(List<Expense> expenses,
                              BigDecimal totalExpense,
                              long placesCount) {
    }
}
