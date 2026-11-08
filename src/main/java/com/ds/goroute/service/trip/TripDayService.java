package com.ds.goroute.service.trip;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.TripService;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Changes to a trip's days: removing, inserting, moving or swapping days, moving the whole trip,
 * and giving it a new date range. The itinerary screen and applied Pengoo proposals both go here.
 *
 * <p>Activities belong to a day <em>number</em> counted from the trip's start, so every change is
 * a renumbering: one statement and one {@code activity.reordered} event instead of an update (and
 * a notification) per activity. Removed days delete their activities through
 * {@link ActivityService}, which cleans up what hangs off them. The trip's dates change through
 * {@link TripService} so the members are told once.
 *
 * <p>Activities outside the trip's days (a bus taken the day before, numbered 0 or below, or a
 * flight home after the last day) keep their calendar date through every change except moving
 * the whole trip, which takes them along.
 *
 * <p>Destination date ranges follow their days: moved with the days that moved, shortened or
 * emptied when their days were removed, and stretched over new empty days next to them.
 */
@Service
@RequiredArgsConstructor
public class TripDayService {

    /** The longest trip a day change may produce. */
    public static final int MAX_DAYS = 400;

    private final TripAccessGuard accessGuard;
    private final ActivityRepository activities;
    private final ActivityService activityService;
    private final TripService tripService;
    private final TripDestinationRepository destinations;
    private final TripRealtimePublisher realtime;

    /** Lengthens the trip at its end so it has {@code lastDay}; a trip already that long, or without dates, is left alone. */
    @Transactional
    public void extendTo(UUID tripId, int lastDay, UUID actorId) {
        Trip trip = accessGuard.requireEditAccess(tripId, actorId);
        if (trip.getStartDate() == null || trip.getEndDate() == null || dayCount(trip) >= lastDay) return;
        insert(trip, dayCount(trip) + 1, lastDay - dayCount(trip), actorId);
    }

    /**
     * Inserts {@code count} empty days before day {@code beforeDay} (1 to the day after the last).
     * Before day 1 the trip starts earlier and every day keeps its date; elsewhere it ends later
     * and the days from {@code beforeDay} on move later.
     */
    @Transactional
    public void insertDays(UUID tripId, int beforeDay, int count, UUID actorId) {
        Trip trip = datedTrip(tripId, actorId);
        insert(trip, beforeDay, count, actorId);
    }

    /**
     * Removes days {@code fromDay}..{@code toDay} and their activities. Without the first day the
     * trip starts later and the other days keep their dates; otherwise it ends earlier and the
     * days after the removed ones move earlier.
     */
    @Transactional
    public void removeDays(UUID tripId, int fromDay, int toDay, UUID actorId) {
        Trip trip = datedTrip(tripId, actorId);
        int days = dayCount(trip);
        int count = toDay - fromDay + 1;
        if (fromDay < 1 || toDay < fromDay || toDay > days) throw invalid("Those days are not in the trip");
        if (count >= days) throw invalid("A trip keeps at least one day");
        int[] dayOf = new int[days + 1];
        for (int day = 1; day <= days; day++) {
            dayOf[day] = day < fromDay ? day : day > toDay ? day - count : 0;
        }
        LocalDate start = fromDay == 1 ? trip.getStartDate().plusDays(count) : trip.getStartDate();
        LocalDate end = fromDay == 1 ? trip.getEndDate() : trip.getEndDate().minusDays(count);
        apply(trip, new Layout(start, end, dayOf, Map.of(), false, false), actorId);
    }

    /** Moves the plan of day {@code fromDay} to position {@code toDay}; the days between close up. Dates stay. */
    @Transactional
    public void moveDay(UUID tripId, int fromDay, int toDay, UUID actorId) {
        Trip trip = datedTrip(tripId, actorId);
        int days = dayCount(trip);
        if (fromDay < 1 || fromDay > days || toDay < 1 || toDay > days) throw invalid("Those days are not in the trip");
        if (fromDay == toDay) return;
        List<Integer> order = new ArrayList<>();
        for (int day = 1; day <= days; day++) order.add(day);
        order.remove(Integer.valueOf(fromDay));
        order.add(toDay - 1, fromDay);
        int[] dayOf = new int[days + 1];
        for (int position = 0; position < order.size(); position++) dayOf[order.get(position)] = position + 1;
        apply(trip, new Layout(trip.getStartDate(), trip.getEndDate(), dayOf, Map.of(), false, true), actorId);
    }

    /** Exchanges the plans of two days; the trip's dates stay. */
    @Transactional
    public void swapDays(UUID tripId, int firstDay, int secondDay, UUID actorId) {
        Trip trip = datedTrip(tripId, actorId);
        int days = dayCount(trip);
        if (firstDay < 1 || firstDay > days || secondDay < 1 || secondDay > days) throw invalid("Those days are not in the trip");
        if (firstDay == secondDay) return;
        int[] dayOf = identity(days);
        dayOf[firstDay] = secondDay;
        dayOf[secondDay] = firstDay;
        apply(trip, new Layout(trip.getStartDate(), trip.getEndDate(), dayOf, Map.of(), false, true), actorId);
    }

    /** Moves the whole trip to start on {@code newStart}: same length, same plan for each day, outside activities along. */
    @Transactional
    public void shiftDates(UUID tripId, LocalDate newStart, UUID actorId) {
        Trip trip = datedTrip(tripId, actorId);
        int days = dayCount(trip);
        if (newStart.equals(trip.getStartDate())) return;
        apply(trip, new Layout(newStart, newStart.plusDays(days - 1L), identity(days), Map.of(), true, false), actorId);
    }

    /**
     * Gives the trip new dates. With {@code keepDates} every day whose date is still in the new
     * range keeps its date (and its plan); otherwise the plan keeps its order from the new start,
     * day 1 still day 1, and the activities outside the trip move with it. Days left out are
     * removed with their activities, unless {@code mergeInto} names the new day (1-based, in the
     * new range) their activities join.
     */
    @Transactional
    public void reschedule(UUID tripId, LocalDate newStart, LocalDate newEnd, boolean keepDates,
                           Map<Integer, Integer> mergeInto, UUID actorId) {
        Trip trip = datedTrip(tripId, actorId);
        if (newStart == null || newEnd == null || newEnd.isBefore(newStart)) throw invalid("The trip must end on or after its start");
        long newDays = ChronoUnit.DAYS.between(newStart, newEnd) + 1;
        if (newDays > MAX_DAYS) throw invalid("A trip lasts at most " + MAX_DAYS + " days");
        int days = dayCount(trip);
        int startDelta = (int) ChronoUnit.DAYS.between(trip.getStartDate(), newStart);
        int[] dayOf = new int[days + 1];
        for (int day = 1; day <= days; day++) {
            int target = keepDates ? day - startDelta : day;
            dayOf[day] = target >= 1 && target <= newDays ? target : 0;
        }
        Map<Integer, Integer> merges = new HashMap<>();
        if (mergeInto != null) {
            mergeInto.forEach((oldDay, newDay) -> {
                if (oldDay == null || newDay == null || oldDay < 1 || oldDay > days || dayOf[oldDay] != 0) return;
                if (newDay < 1 || newDay > newDays) throw invalid("Activities can only move to a day of the trip");
                merges.put(oldDay, newDay);
            });
        }
        apply(trip, new Layout(newStart, newEnd, dayOf, merges, !keepDates, false), actorId);
    }

    /** Days in the trip; 1 when the dates are missing. */
    public static int dayCount(Trip trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null) return 1;
        long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        return (int) Math.max(1, Math.min(days, MAX_DAYS));
    }

    private void insert(Trip trip, int beforeDay, int count, UUID actorId) {
        int days = dayCount(trip);
        if (beforeDay < 1 || beforeDay > days + 1) throw invalid("Days can only be inserted next to a day of the trip");
        if (count < 1 || days + count > MAX_DAYS) throw invalid("A trip lasts at most " + MAX_DAYS + " days");
        int[] dayOf = new int[days + 1];
        for (int day = 1; day <= days; day++) dayOf[day] = day < beforeDay ? day : day + count;
        LocalDate start = beforeDay == 1 ? trip.getStartDate().minusDays(count) : trip.getStartDate();
        LocalDate end = beforeDay == 1 ? trip.getEndDate() : trip.getEndDate().plusDays(count);
        apply(trip, new Layout(start, end, dayOf, Map.of(), false, false), actorId);
    }

    /**
     * A new layout of the days.
     *
     * @param dayOf             for each old day (index 1..N), its new number; 0 when it is left out
     * @param mergeInto         left-out days whose activities join a kept day instead of being deleted
     * @param outsideFollow     whether activities outside the old days keep their number (move with
     *                          the trip) rather than their calendar date
     * @param keepDestinations  whether destination ranges stay as they are (the days were only reordered)
     */
    record Layout(LocalDate start, LocalDate end, int[] dayOf, Map<Integer, Integer> mergeInto,
                  boolean outsideFollow, boolean keepDestinations) {
    }

    private void apply(Trip trip, Layout layout, UUID actorId) {
        UUID tripId = trip.getId();
        int days = layout.dayOf().length - 1;
        for (Activity activity : activities.findByTripId(tripId)) {
            Integer day = activity.getDayNumber();
            if (day != null && day >= 1 && day <= days && layout.dayOf()[day] == 0
                    && !layout.mergeInto().containsKey(day)) {
                activityService.deleteActivity(tripId, activity.getId(), actorId);
            }
        }
        int startDelta = (int) ChronoUnit.DAYS.between(trip.getStartDate(), layout.start());
        Map<Integer, Integer> moves = new LinkedHashMap<>();
        for (int day = 1; day <= days; day++) {
            int target = layout.dayOf()[day] != 0 ? layout.dayOf()[day] : layout.mergeInto().getOrDefault(day, 0);
            if (target != 0 && target != day) moves.put(day, target);
        }
        int outsideDelta = layout.outsideFollow() ? 0 : -startDelta;
        if ((!moves.isEmpty() || startDelta != 0)
                && activities.relayoutDays(tripId, days, moves, outsideDelta, startDelta) > 0) {
            realtime.publishAfterCommit(TripRealtimeEventType.ACTIVITY_REORDERED, tripId, tripId, actorId);
        }
        if (layout.start().equals(trip.getStartDate()) && layout.end().equals(trip.getEndDate())) return;
        tripService.updateTrip(tripId,
                UpdateTripRequest.builder().startDate(layout.start()).endDate(layout.end()).build(), actorId);
        if (!layout.keepDestinations()) relayoutDestinations(trip, layout);
    }

    /**
     * Maps each dated destination's days through the layout: the new range runs from the first to
     * the last new day of its kept days (none kept: no dates). New empty days then join the
     * destination of the day before them, or at the very start the one of the day after.
     */
    private void relayoutDestinations(Trip trip, Layout layout) {
        int days = layout.dayOf().length - 1;
        int newDays = (int) ChronoUnit.DAYS.between(layout.start(), layout.end()) + 1;
        boolean[] kept = new boolean[newDays + 2];
        for (int day = 1; day <= days; day++) {
            int target = layout.dayOf()[day];
            if (target >= 1 && target <= newDays) kept[target] = true;
        }
        List<TripDestination> dated = new ArrayList<>();
        List<int[]> ranges = new ArrayList<>();
        for (TripDestination destination : destinations.findByTripId(trip.getId())) {
            if (destination.getStartDate() == null || destination.getEndDate() == null) continue;
            int first = Math.max(1, dayOf(trip.getStartDate(), destination.getStartDate()));
            int last = Math.min(days, dayOf(trip.getStartDate(), destination.getEndDate()));
            int from = Integer.MAX_VALUE;
            int to = 0;
            for (int day = first; day <= last; day++) {
                int target = layout.dayOf()[day];
                if (target < 1 || target > newDays) continue;
                from = Math.min(from, target);
                to = Math.max(to, target);
            }
            dated.add(destination);
            ranges.add(to == 0 ? null : new int[]{from, to});
        }
        for (int day = 1; day <= newDays; day++) {
            if (kept[day]) continue;
            int[] before = covering(ranges, day - 1);
            if (before != null) before[1] = day;
        }
        for (int day = newDays; day >= 1; day--) {
            if (kept[day] || covering(ranges, day) != null) continue;
            int[] after = covering(ranges, day + 1);
            if (after != null) after[0] = day;
        }
        for (int i = 0; i < dated.size(); i++) {
            TripDestination destination = dated.get(i);
            int[] range = ranges.get(i);
            LocalDate from = range == null ? null : layout.start().plusDays(range[0] - 1L);
            LocalDate to = range == null ? null : layout.start().plusDays(range[1] - 1L);
            if (!Objects.equals(from, destination.getStartDate()) || !Objects.equals(to, destination.getEndDate())) {
                destinations.updateDates(destination.getId(), from, to);
            }
        }
    }

    private static int[] covering(List<int[]> ranges, int day) {
        for (int[] range : ranges) {
            if (range != null && range[0] <= day && day <= range[1]) return range;
        }
        return null;
    }

    private static int dayOf(LocalDate tripStart, LocalDate date) {
        return (int) ChronoUnit.DAYS.between(tripStart, date) + 1;
    }

    private static int[] identity(int days) {
        int[] dayOf = new int[days + 1];
        for (int day = 1; day <= days; day++) dayOf[day] = day;
        return dayOf;
    }

    private Trip datedTrip(UUID tripId, UUID actorId) {
        Trip trip = accessGuard.requireEditAccess(tripId, actorId);
        if (trip.getStartDate() == null || trip.getEndDate() == null) {
            throw invalid("The trip has no dates");
        }
        return trip;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
