package com.ds.goroute.service.assistant;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripDestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A trip as the assistant reads it: dates, destinations and every day's activities in order,
 * compact enough to send with every question (ids so a proposal can name an activity).
 */
@Component
@RequiredArgsConstructor
public class AssistantTripSnapshots {

    static final int MAX_DAYS = 30;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ActivityRepository activities;
    private final TripDestinationRepository destinations;

    public Map<String, Object> snapshot(Trip trip) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", trip.getId());
        out.put("name", trip.getName());
        out.put("startDate", trip.getStartDate());
        out.put("endDate", trip.getEndDate());
        out.put("destinations", destinationRows(trip));
        out.put("days", dayRows(trip));
        return out;
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
