package com.ds.goroute.service.assistant;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A trip as the assistant reads it: dates, which day today is, destinations, who is going,
 * where they start, the budget, and every day's activities in order, compact enough to send with
 * every question (ids so a proposal can name an activity).
 */
@Component
@RequiredArgsConstructor
public class AssistantTripSnapshots {

    static final int MAX_DAYS = 30;
    static final int MAX_MEMBERS = 20;
    static final int MAX_NOTE = 80;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ActivityRepository activities;
    private final TripDestinationRepository destinations;
    private final TripMemberRepository members;
    private final UserRepository users;

    public Map<String, Object> snapshot(Trip trip) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", trip.getId());
        out.put("name", trip.getName());
        out.put("startDate", trip.getStartDate());
        out.put("endDate", trip.getEndDate());
        out.put("dayCount", dayCount(trip));
        todayRow(trip, LocalDate.now(LOCAL_ZONE)).ifPresent(today -> out.put("today", today));
        out.put("destinations", destinationRows(trip));
        List<Map<String, Object>> people = memberRows(trip);
        if (!people.isEmpty()) out.put("members", people);
        startingPointRow(trip).ifPresent(start -> out.put("startingPoint", start));
        if (trip.getBudget() != null && trip.getBudget().signum() > 0) {
            out.put("budget", Map.of("amount", trip.getBudget(),
                    "currency", trip.getCurrency() == null ? "VND" : trip.getCurrency()));
        }
        out.put("days", dayRows(trip));
        return out;
    }

    /**
     * Where {@code today} falls in the trip: its day number while it runs, otherwise how many
     * days until it starts or since it ended, so "what's next" and "tomorrow" need no arithmetic.
     */
    static Optional<Map<String, Object>> todayRow(Trip trip, LocalDate today) {
        if (trip.getStartDate() == null || trip.getEndDate() == null) return Optional.empty();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", today);
        if (today.isBefore(trip.getStartDate())) {
            row.put("startsInDays", ChronoUnit.DAYS.between(today, trip.getStartDate()));
        } else if (today.isAfter(trip.getEndDate())) {
            row.put("endedDaysAgo", ChronoUnit.DAYS.between(trip.getEndDate(), today));
        } else {
            row.put("dayNumber", ChronoUnit.DAYS.between(trip.getStartDate(), today) + 1);
        }
        return Optional.of(row);
    }

    private static Optional<Map<String, Object>> startingPointRow(Trip trip) {
        if (trip.getStartingPointName() == null && trip.getStartingPointAddress() == null) return Optional.empty();
        Map<String, Object> start = new LinkedHashMap<>();
        if (trip.getStartingPointName() != null) start.put("name", trip.getStartingPointName());
        if (trip.getStartingPointAddress() != null) start.put("address", trip.getStartingPointAddress());
        if (trip.getStartingPointTime() != null) start.put("time", trip.getStartingPointTime());
        putCoordinates(start, trip.getStartingPointLat(), trip.getStartingPointLng());
        return Optional.of(start);
    }

    /** Who is on the trip (accepted members and guests), by name and role; no ids or contacts. */
    private List<Map<String, Object>> memberRows(Trip trip) {
        List<TripMember> accepted = members.findByTripId(trip.getId()).stream()
                .filter(m -> m.getStatus() == null || m.getStatus() == MemberStatus.ACCEPTED)
                .limit(MAX_MEMBERS)
                .toList();
        List<UUID> userIds = new ArrayList<>();
        if (trip.getOwnerId() != null) userIds.add(trip.getOwnerId());
        accepted.stream().map(TripMember::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : users.findByIds(userIds)) {
                names.put(user.getId(), user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean ownerListed = false;
        for (TripMember member : accepted) {
            String name = member.getUserId() != null ? names.get(member.getUserId()) : member.getGuestName();
            if (name == null || name.isBlank()) continue;
            boolean owner = member.getRole() == MemberRole.OWNER
                    || (member.getUserId() != null && member.getUserId().equals(trip.getOwnerId()));
            ownerListed |= owner;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("role", owner ? MemberRole.OWNER.name()
                    : member.getRole() == null ? MemberRole.VIEWER.name() : member.getRole().name());
            if (Boolean.TRUE.equals(member.getIsGuest())) row.put("guest", true);
            rows.add(row);
        }
        String ownerName = trip.getOwnerId() == null ? null : names.get(trip.getOwnerId());
        if (!ownerListed && ownerName != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", ownerName);
            row.put("role", MemberRole.OWNER.name());
            rows.add(0, row);
        }
        return rows;
    }

    /** Number of days, 1 when the dates are missing. */
    public static int dayCount(Trip trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null) return 1;
        long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        return (int) Math.max(1, Math.min(days, 400));
    }

    public static Optional<LocalDate> dateOfDay(Trip trip, int dayNumber) {
        return trip.getStartDate() == null ? Optional.empty() : Optional.of(trip.getStartDate().plusDays(dayNumber - 1L));
    }

    /** Catalogue place id of an activity: the explicit link, else a UUID stored as its place id. */
    public static UUID catalogueId(Activity activity) {
        if (activity.getPlaceRefId() != null) return activity.getPlaceRefId();
        String raw = activity.getPlaceId();
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<TripDestination> destinationsOf(Trip trip) {
        return destinations.findByTripId(trip.getId()).stream()
                .sorted(Comparator.comparing(d -> d.getOrderIndex() == null ? 0 : d.getOrderIndex()))
                .toList();
    }

    public List<Activity> activitiesOf(Trip trip) {
        return activities.findByTripId(trip.getId());
    }

    private List<Map<String, Object>> destinationRows(Trip trip) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TripDestination destination : destinationsOf(trip)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", destination.getName());
            putCoordinates(row, destination.getLat(), destination.getLng());
            row.put("startDate", destination.getStartDate());
            row.put("endDate", destination.getEndDate());
            rows.add(row);
        }
        if (rows.isEmpty() && trip.getDestinationLat() != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", trip.getDestination());
            putCoordinates(row, trip.getDestinationLat(), trip.getDestinationLng());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> dayRows(Trip trip) {
        int days = Math.min(dayCount(trip), MAX_DAYS);
        Map<Integer, List<Activity>> byDay = new LinkedHashMap<>();
        for (Activity activity : activitiesOf(trip)) {
            if (activity.getDayNumber() == null || activity.getDayNumber() < 1 || activity.getDayNumber() > days) continue;
            byDay.computeIfAbsent(activity.getDayNumber(), key -> new ArrayList<>()).add(activity);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day);
            dateOfDay(trip, day).ifPresent(date -> row.put("date", date));
            List<Map<String, Object>> items = new ArrayList<>();
            for (Activity activity : sorted(byDay.getOrDefault(day, List.of()))) {
                items.add(activityRow(activity));
            }
            row.put("activities", items);
            rows.add(row);
        }
        return rows;
    }

    private static List<Activity> sorted(List<Activity> dayActivities) {
        return dayActivities.stream()
                .sorted(Comparator.comparing((Activity a) -> a.getStartTime() == null ? LocalTime.MAX : a.getStartTime())
                        .thenComparing(a -> a.getSortOrder() == null ? 0 : a.getSortOrder()))
                .toList();
    }

    private static Map<String, Object> activityRow(Activity activity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", activity.getId());
        row.put("name", activity.getName());
        if (activity.getStartTime() != null) row.put("start", activity.getStartTime().format(TIME));
        if (activity.getEndTime() != null) row.put("end", activity.getEndTime().format(TIME));
        if (activity.getCategory() != null) row.put("category", activity.getCategory());
        if (Boolean.TRUE.equals(activity.getIsAccommodation())) row.put("stay", true);
        if (activity.getEndDayNumber() != null && !activity.getEndDayNumber().equals(activity.getDayNumber())) {
            row.put("untilDay", activity.getEndDayNumber());
        }
        if (activity.getEstimatedCost() != null && activity.getEstimatedCost().signum() > 0) {
            row.put("cost", activity.getEstimatedCost().stripTrailingZeros().toPlainString()
                    + (activity.getCostCurrency() == null ? "" : " " + activity.getCostCurrency()));
        }
        if (activity.getNotes() != null && !activity.getNotes().isBlank()) {
            String note = activity.getNotes().trim().replaceAll("\\s+", " ");
            row.put("note", note.length() > MAX_NOTE ? note.substring(0, MAX_NOTE) + "…" : note);
        }
        UUID placeId = catalogueId(activity);
        if (placeId != null) row.put("placeId", placeId);
        putCoordinates(row, activity.getLat(), activity.getLng());
        return row;
    }

    private static void putCoordinates(Map<String, Object> row, BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) return;
        row.put("lat", lat.setScale(4, java.math.RoundingMode.HALF_UP));
        row.put("lng", lng.setScale(4, java.math.RoundingMode.HALF_UP));
    }
}
