package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripDestination;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.TripService;
import com.ds.goroute.type.TripRealtimeEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Changes to a trip's days that an applied Pengoo proposal makes: lengthening, shortening,
 * starting earlier, swapping two days, moving the whole trip.
 *
 * <p>Activities belong to a day <em>number</em>, so these are renumberings: each runs as one
 * statement and one {@code activity.reordered} event instead of an update (and a notification)
 * per activity. Removed days delete their activities through {@link ActivityService}, which
 * cleans up what hangs off them. The trip's dates change through {@link TripService} so the
 * members are told once, by the person who applied. Destination date ranges follow their days:
 * stretched when they touched the old start or end, moved with the days that moved, emptied
 * when all their days were removed.
 *
 * <p>Callers have checked the edit right and run inside their transaction.
 */
@Component
@RequiredArgsConstructor
public class AssistantTripDays {

    private final TripRepository trips;
    private final ActivityRepository activities;
    private final ActivityService activityService;
    private final TripService tripService;
    private final TripDestinationRepository destinations;
    private final TripRealtimePublisher realtime;

    /** Lengthens the trip at its end so it has {@code lastDay}; a trip already that long is left alone. */
    public void extendTo(UUID tripId, int lastDay, UUID actorId) {
        Trip trip = trip(tripId);
        if (trip.getStartDate() == null || AssistantTripSnapshots.dayCount(trip) >= lastDay) return;
        LocalDate oldEnd = trip.getEndDate();
        LocalDate newEnd = trip.getStartDate().plusDays(lastDay - 1L);
        setDates(trip, trip.getStartDate(), newEnd, d -> d, d -> d.equals(oldEnd) ? newEnd : d, actorId);
    }

    /** Adds {@code days} days before day 1: every activity keeps its calendar date. */
    public void prepend(UUID tripId, int days, UUID actorId) {
        Trip trip = datedTrip(tripId);
        if (activities.shiftDays(tripId, 1, days, 0) > 0) activitiesMoved(tripId, actorId);
        LocalDate oldStart = trip.getStartDate();
        LocalDate newStart = oldStart.minusDays(days);
        setDates(trip, newStart, trip.getEndDate(), d -> d.equals(oldStart) ? newStart : d, d -> d, actorId);
    }

    /**
     * Removes days {@code fromDay}..{@code toDay} and their activities. Without the first day the
     * trip starts later and the other days keep their dates; otherwise it ends earlier and the
     * days after the removed ones move earlier.
     */
    public void removeDays(UUID tripId, int fromDay, int toDay, UUID actorId) {
        Trip trip = datedTrip(tripId);
        int count = toDay - fromDay + 1;
        if (count >= AssistantTripSnapshots.dayCount(trip)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A trip keeps at least one day");
        }
        for (Activity activity : activities.findByTripId(tripId)) {
            Integer day = activity.getDayNumber();
            if (day != null && day >= fromDay && day <= toDay) {
                activityService.deleteActivity(tripId, activity.getId(), actorId);
            }
        }
        boolean fromStart = fromDay == 1;
        if (activities.shiftDays(tripId, toDay + 1, -count, fromStart ? 0 : -count) > 0) {
            activitiesMoved(tripId, actorId);
        }
        LocalDate removedFirst = trip.getStartDate().plusDays(fromDay - 1L);
        LocalDate removedLast = trip.getStartDate().plusDays(toDay - 1L);
        if (fromStart) {
            LocalDate newStart = removedLast.plusDays(1);
            setDates(trip, newStart, trip.getEndDate(), d -> d.isBefore(newStart) ? newStart : d, d -> d, actorId);
        } else {
            // Later days move up by the removed count; a range ending inside the removed days ends
            // the day before them, one starting inside starts where the next days now are.
            setDates(trip, trip.getStartDate(), trip.getEndDate().minusDays(count),
                    d -> d.isBefore(removedFirst) ? d : d.isAfter(removedLast) ? d.minusDays(count) : removedFirst,
                    d -> d.isBefore(removedFirst) ? d : d.isAfter(removedLast) ? d.minusDays(count) : removedFirst.minusDays(1),
                    actorId);
        }
    }

    /** Exchanges the plans of two days; the trip's dates stay. */
    public void swapDays(UUID tripId, int firstDay, int secondDay, UUID actorId) {
        trip(tripId);
        if (activities.swapDays(tripId, firstDay, secondDay) > 0) activitiesMoved(tripId, actorId);
    }

    /** Moves the whole trip to start on {@code newStart}, same length, same plan for each day. */
    public void shiftDates(UUID tripId, LocalDate newStart, UUID actorId) {
        Trip trip = datedTrip(tripId);
        int delta = (int) ChronoUnit.DAYS.between(trip.getStartDate(), newStart);
        if (delta == 0) return;
        activities.shiftDays(tripId, 1, 0, delta);
        setDates(trip, newStart, trip.getEndDate().plusDays(delta), d -> d.plusDays(delta), d -> d.plusDays(delta), actorId);
    }

    /**
     * Sets the trip's dates, then maps each dated destination's first and last date through
     * {@code mapStart}/{@code mapEnd} and keeps the result inside the new range; a range left
     * empty loses its dates.
     */
    private void setDates(Trip trip, LocalDate start, LocalDate end, UnaryOperator<LocalDate> mapStart,
                          UnaryOperator<LocalDate> mapEnd, UUID actorId) {
        tripService.updateTrip(trip.getId(), UpdateTripRequest.builder().startDate(start).endDate(end).build(), actorId);
        for (TripDestination destination : destinations.findByTripId(trip.getId())) {
            if (destination.getStartDate() == null || destination.getEndDate() == null) continue;
            LocalDate from = mapStart.apply(destination.getStartDate());
            LocalDate to = mapEnd.apply(destination.getEndDate());
            if (from.isBefore(start)) from = start;
            if (to.isAfter(end)) to = end;
            boolean empty = from.isAfter(to);
            LocalDate newFrom = empty ? null : from;
            LocalDate newTo = empty ? null : to;
            if (!Objects.equals(newFrom, destination.getStartDate()) || !Objects.equals(newTo, destination.getEndDate())) {
                destinations.updateDates(destination.getId(), newFrom, newTo);
            }
        }
    }

    private void activitiesMoved(UUID tripId, UUID actorId) {
        realtime.publishAfterCommit(TripRealtimeEventType.ACTIVITY_REORDERED, tripId, tripId, actorId);
    }

    private Trip trip(UUID tripId) {
        return trips.findById(tripId).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
    }

    private Trip datedTrip(UUID tripId) {
        Trip trip = trip(tripId);
        if (trip.getStartDate() == null || trip.getEndDate() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The trip has no dates");
        }
        return trip;
    }
}
