package com.ds.goroute.service.assistant;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantTripDaysTest {

    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID EDITOR = UUID.randomUUID();
    private static final UUID FIRST_STOP = UUID.randomUUID();
    private static final UUID SECOND_STOP = UUID.randomUUID();

    @Mock private TripRepository trips;
    @Mock private ActivityRepository activities;
    @Mock private ActivityService activityService;
    @Mock private TripService tripService;
    @Mock private TripDestinationRepository destinations;
    @Mock private TripRealtimePublisher realtime;

    private AssistantTripDays tripDays;

    /** Nov 1-4: Đà Nẵng days 1-2, Hội An days 3-4. */
    @BeforeEach
    void setUp() {
        tripDays = new AssistantTripDays(trips, activities, activityService, tripService, destinations, realtime);
        when(trips.findById(TRIP)).thenReturn(Optional.of(Trip.builder().id(TRIP)
                .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 4)).build()));
        when(destinations.findByTripId(TRIP)).thenReturn(List.of(
                TripDestination.builder().id(FIRST_STOP).tripId(TRIP)
                        .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 2)).build(),
                TripDestination.builder().id(SECOND_STOP).tripId(TRIP)
                        .startDate(LocalDate.of(2026, 11, 3)).endDate(LocalDate.of(2026, 11, 4)).build()));
        when(activities.shiftDays(any(), anyInt(), anyInt(), anyInt())).thenReturn(2);
        when(activities.swapDays(any(), anyInt(), anyInt())).thenReturn(2);
    }

    private UpdateTripRequest tripUpdate() {
        ArgumentCaptor<UpdateTripRequest> update = ArgumentCaptor.forClass(UpdateTripRequest.class);
        verify(tripService).updateTrip(eq(TRIP), update.capture(), eq(EDITOR));
        return update.getValue();
    }

    @Test
    void extendingStretchesTheLastStopAndLeavesALongEnoughTripAlone() {
        tripDays.extendTo(TRIP, 5, EDITOR);

        assertThat(tripUpdate().getEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        verify(destinations).updateDates(SECOND_STOP, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5));
        verify(destinations, never()).updateDates(eq(FIRST_STOP), any(), any());

        tripDays.extendTo(TRIP, 4, EDITOR);
        verify(tripService, org.mockito.Mockito.times(1)).updateTrip(any(), any(), any());
    }

    @Test
    void arrivingEarlierRenumbersEveryDayButKeepsTheirDates() {
        tripDays.prepend(TRIP, 2, EDITOR);

        verify(activities).shiftDays(TRIP, 1, 2, 0);
        verify(realtime).publishAfterCommit(TripRealtimeEventType.ACTIVITY_REORDERED, TRIP, TRIP, EDITOR);
        assertThat(tripUpdate().getStartDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        verify(destinations).updateDates(FIRST_STOP, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2));
    }

    @Test
    void removingAMiddleDayDeletesItsPlanAndPullsTheLaterDaysEarlier() {
        UUID onDay2 = UUID.randomUUID();
        when(activities.findByTripId(TRIP)).thenReturn(List.of(
                Activity.builder().id(UUID.randomUUID()).tripId(TRIP).dayNumber(1).build(),
                Activity.builder().id(onDay2).tripId(TRIP).dayNumber(2).build()));

        tripDays.removeDays(TRIP, 2, 2, EDITOR);

        verify(activityService).deleteActivity(TRIP, onDay2, EDITOR);
        verify(activityService, org.mockito.Mockito.times(1)).deleteActivity(any(), any(), any());
        verify(activities).shiftDays(TRIP, 3, -1, -1);
        UpdateTripRequest update = tripUpdate();
        assertThat(update.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(update.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        verify(destinations).updateDates(FIRST_STOP, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
        verify(destinations).updateDates(SECOND_STOP, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));
    }

    @Test
    void removingTheFirstDaysStartsTheTripLaterAndKeepsTheOtherDates() {
        when(activities.findByTripId(TRIP)).thenReturn(List.of());

        tripDays.removeDays(TRIP, 1, 2, EDITOR);

        verify(activities).shiftDays(TRIP, 3, -2, 0);
        assertThat(tripUpdate().getStartDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        verify(destinations).updateDates(FIRST_STOP, null, null);
    }

    @Test
    void aTripKeepsAtLeastOneDay() {
        assertThatThrownBy(() -> tripDays.removeDays(TRIP, 1, 4, EDITOR)).isInstanceOf(BusinessException.class);
        verify(tripService, never()).updateTrip(any(), any(), any());
    }

    @Test
    void swappingDaysMovesActivitiesOnly() {
        tripDays.swapDays(TRIP, 1, 3, EDITOR);

        verify(activities).swapDays(TRIP, 1, 3);
        verify(realtime).publishAfterCommit(TripRealtimeEventType.ACTIVITY_REORDERED, TRIP, TRIP, EDITOR);
        verify(tripService, never()).updateTrip(any(), any(), any());
    }

    @Test
    void movingTheTripMovesEveryDateTogether() {
        tripDays.shiftDates(TRIP, LocalDate.of(2026, 11, 8), EDITOR);

        verify(activities).shiftDays(TRIP, 1, 0, 7);
        UpdateTripRequest update = tripUpdate();
        assertThat(update.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(update.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        verify(destinations).updateDates(FIRST_STOP, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9));
        verify(destinations).updateDates(SECOND_STOP, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));
    }
}
