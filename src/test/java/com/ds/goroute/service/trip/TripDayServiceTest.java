package com.ds.goroute.service.trip;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripDayServiceTest {

    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID EDITOR = UUID.randomUUID();
    private static final UUID FIRST_STOP = UUID.randomUUID();
    private static final UUID SECOND_STOP = UUID.randomUUID();

    @Mock private TripAccessGuard accessGuard;
    @Mock private ActivityRepository activities;
    @Mock private ActivityService activityService;
    @Mock private TripService tripService;
    @Mock private TripDestinationRepository destinations;
    @Mock private TripRealtimePublisher realtime;

    private TripDayService tripDays;

    /** Nov 1-4: Đà Nẵng days 1-2, Hội An days 3-4. */
    @BeforeEach
    void setUp() {
        tripDays = new TripDayService(accessGuard, activities, activityService, tripService, destinations, realtime);
        when(accessGuard.requireEditAccess(TRIP, EDITOR)).thenReturn(Trip.builder().id(TRIP)
                .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 4)).build());
        when(destinations.findByTripId(TRIP)).thenReturn(List.of(
                TripDestination.builder().id(FIRST_STOP).tripId(TRIP)
                        .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 2)).build(),
                TripDestination.builder().id(SECOND_STOP).tripId(TRIP)
                        .startDate(LocalDate.of(2026, 11, 3)).endDate(LocalDate.of(2026, 11, 4)).build()));
        when(activities.relayoutDays(any(), anyInt(), anyMap(), anyInt(), anyInt())).thenReturn(2);
    }

    private UpdateTripRequest tripUpdate() {
        ArgumentCaptor<UpdateTripRequest> update = ArgumentCaptor.forClass(UpdateTripRequest.class);
        verify(tripService).updateTrip(eq(TRIP), update.capture(), eq(EDITOR));
        return update.getValue();
    }

    private static LocalDate nov(int day) {
        return LocalDate.of(2026, 11, day);
    }

    @Test
    void removingTheFirstDayStartsLaterAndKeepsEveryOtherDate() {
        tripDays.removeDays(TRIP, 1, 1, EDITOR);

        verify(activities).relayoutDays(TRIP, 4, Map.of(2, 1, 3, 2, 4, 3), -1, 1);
        assertThat(tripUpdate().getStartDate()).isEqualTo(nov(2));
        verify(destinations).updateDates(FIRST_STOP, nov(2), nov(2));
        verify(destinations, never()).updateDates(eq(SECOND_STOP), any(), any());
    }

    @Test
    void removingAMiddleDayDeletesItsActivitiesAndPullsTheLaterDaysForward() {
        UUID onDayTwo = UUID.randomUUID();
        when(activities.findByTripId(TRIP)).thenReturn(List.of(
                Activity.builder().id(onDayTwo).tripId(TRIP).dayNumber(2).build(),
                Activity.builder().id(UUID.randomUUID()).tripId(TRIP).dayNumber(0).build()));

        tripDays.removeDays(TRIP, 2, 2, EDITOR);

        verify(activityService).deleteActivity(TRIP, onDayTwo, EDITOR);
        verify(activities).relayoutDays(TRIP, 4, Map.of(3, 2, 4, 3), 0, 0);
        assertThat(tripUpdate().getEndDate()).isEqualTo(nov(3));
        verify(destinations).updateDates(FIRST_STOP, nov(1), nov(1));
        verify(destinations).updateDates(SECOND_STOP, nov(2), nov(3));
    }

    @Test
    void aTripKeepsAtLeastOneDay() {
        assertThatThrownBy(() -> tripDays.removeDays(TRIP, 1, 4, EDITOR)).isInstanceOf(BusinessException.class);
    }

    @Test
    void anInsertedMiddleDayJoinsTheStopOfTheDayBefore() {
        tripDays.insertDays(TRIP, 3, 1, EDITOR);

        verify(activities).relayoutDays(TRIP, 4, Map.of(3, 4, 4, 5), 0, 0);
        assertThat(tripUpdate().getEndDate()).isEqualTo(nov(5));
        verify(destinations).updateDates(FIRST_STOP, nov(1), nov(3));
        verify(destinations).updateDates(SECOND_STOP, nov(4), nov(5));
    }

    @Test
    void daysInsertedBeforeDayOneStartTheTripEarlierAndKeepEveryDate() {
        tripDays.insertDays(TRIP, 1, 2, EDITOR);

        verify(activities).relayoutDays(TRIP, 4, Map.of(1, 3, 2, 4, 3, 5, 4, 6), 2, -2);
        assertThat(tripUpdate().getStartDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        verify(destinations).updateDates(FIRST_STOP, LocalDate.of(2026, 10, 30), nov(2));
        verify(destinations, never()).updateDates(eq(SECOND_STOP), any(), any());
    }

    @Test
    void movingADayClosesUpTheDaysBetweenAndKeepsTheDates() {
        tripDays.moveDay(TRIP, 1, 3, EDITOR);

        verify(activities).relayoutDays(TRIP, 4, Map.of(1, 3, 2, 1, 3, 2), 0, 0);
        verify(tripService, never()).updateTrip(any(), any(), any());
    }

    @Test
    void shiftingTheTripTakesTheOutsideActivitiesAlong() {
        tripDays.shiftDates(TRIP, nov(8), EDITOR);

        verify(activities).relayoutDays(TRIP, 4, Map.of(), 0, 7);
        assertThat(tripUpdate().getEndDate()).isEqualTo(nov(11));
        verify(destinations).updateDates(FIRST_STOP, nov(8), nov(9));
        verify(destinations).updateDates(SECOND_STOP, nov(10), nov(11));
    }

    @Test
    void reschedulingByDateMergesADroppedDayInsteadOfDeletingIt() {
        UUID onDayOne = UUID.randomUUID();
        when(activities.findByTripId(TRIP)).thenReturn(List.of(
                Activity.builder().id(onDayOne).tripId(TRIP).dayNumber(1).build()));

        tripDays.reschedule(TRIP, nov(2), nov(5), true, Map.of(1, 1), EDITOR);

        verify(activityService, never()).deleteActivity(any(), any(), any());
        verify(activities).relayoutDays(TRIP, 4, Map.of(2, 1, 3, 2, 4, 3), -1, 1);
        verify(destinations).updateDates(FIRST_STOP, nov(2), nov(2));
        verify(destinations).updateDates(SECOND_STOP, nov(3), nov(5));
    }

    @Test
    void reschedulingInOrderKeepsDayOneAsDayOne() {
        tripDays.reschedule(TRIP, nov(10), nov(12), false, Map.of(), EDITOR);

        verify(activities).relayoutDays(TRIP, 4, Map.of(), 0, 9);
        UpdateTripRequest update = tripUpdate();
        assertThat(update.getStartDate()).isEqualTo(nov(10));
        assertThat(update.getEndDate()).isEqualTo(nov(12));
        verify(destinations).updateDates(SECOND_STOP, nov(12), nov(12));
    }
}
