package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.TripCount;
import com.ds.goroute.event.PlaceUsageChangedEvent;
import com.ds.goroute.mapper.ActivityMapper;
import com.ds.goroute.mapper.TripMapper;
import com.ds.goroute.type.ActivityStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every activity write keeps trips.activity_count (V216) in step, and tells which places'
 * usage counts (V225) it may have changed, whoever the caller is.
 */
class ActivityRepositoryImplCounterTest {

    private final ActivityMapper activityMapper = mock(ActivityMapper.class);
    private final TripMapper tripMapper = mock(TripMapper.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ActivityRepositoryImpl repository = new ActivityRepositoryImpl(activityMapper, tripMapper, events);

    @Test
    void insertRecountsItsTrip() {
        UUID tripId = UUID.randomUUID();

        repository.insert(Activity.builder().id(UUID.randomUUID()).tripId(tripId).name("Cafe").build());

        verify(activityMapper).insert(any());
        verify(tripMapper).refreshActivityCount(tripId);
    }

    @Test
    void insertingAStopOnAPlaceMarksThePlace() {
        UUID placeRefId = UUID.randomUUID();

        repository.insert(Activity.builder().id(UUID.randomUUID()).tripId(UUID.randomUUID())
                .placeId("ChIJ-google").placeRefId(placeRefId).build());

        assertThat(publishedReferences()).containsExactlyInAnyOrder("ChIJ-google", placeRefId.toString());
    }

    @Test
    void insertingAStopWithoutAPlaceMarksNothing() {
        repository.insert(Activity.builder().id(UUID.randomUUID()).tripId(UUID.randomUUID()).name("Walk").build());

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void deleteRecountsTheTripTheActivityBelongedTo() {
        UUID tripId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        when(activityMapper.selectById(activityId)).thenReturn(
                Activity.builder().id(activityId).tripId(tripId).placeId("ChIJ-old").build());
        when(activityMapper.deleteById(activityId)).thenReturn(1);

        repository.deleteById(activityId);

        verify(tripMapper).refreshActivityCount(tripId);
        assertThat(publishedReferences()).containsExactly("ChIJ-old");
    }

    @Test
    void deletingNothingTouchesNoTrip() {
        UUID activityId = UUID.randomUUID();
        when(activityMapper.deleteById(activityId)).thenReturn(0);

        repository.deleteById(activityId);

        verify(tripMapper, never()).refreshActivityCount(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void deletingAllOfATripsActivitiesRecountsItAndMarksItsPlaces() {
        UUID tripId = UUID.randomUUID();
        when(activityMapper.selectPlaceReferencesByTripId(tripId)).thenReturn(List.of(
                Activity.builder().placeId("ChIJ-a").build(),
                Activity.builder().placeId("ChIJ-b").build()));

        repository.deleteByTripId(tripId);

        verify(tripMapper).refreshActivityCount(tripId);
        assertThat(publishedReferences()).containsExactlyInAnyOrder("ChIJ-a", "ChIJ-b");
    }

    @Test
    void movingAStopToAnotherPlaceMarksBothPlaces() {
        UUID activityId = UUID.randomUUID();
        when(activityMapper.selectById(activityId)).thenReturn(
                Activity.builder().id(activityId).placeId("ChIJ-old").build());

        repository.updateById(Activity.builder().id(activityId).placeId("ChIJ-new").build());

        assertThat(publishedReferences()).containsExactlyInAnyOrder("ChIJ-old", "ChIJ-new");
    }

    @Test
    void rejectingAStopMarksItsPlace() {
        UUID activityId = UUID.randomUUID();
        when(activityMapper.selectById(activityId)).thenReturn(Activity.builder()
                .id(activityId).placeId("ChIJ-a").status(ActivityStatus.CONFIRMED).build());

        repository.updateById(Activity.builder().id(activityId)
                .status(ActivityStatus.REJECTED).build());

        assertThat(publishedReferences()).containsExactly("ChIJ-a");
    }

    @Test
    void editingATimeMarksNothing() {
        UUID activityId = UUID.randomUUID();
        when(activityMapper.selectById(activityId)).thenReturn(
                Activity.builder().id(activityId).placeId("ChIJ-a").build());

        repository.updateById(Activity.builder().id(activityId).notes("Bring water").build());

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void linkingAStopToTheCatalogueMarksTheOldAndTheNewPlace() {
        UUID activityId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        when(activityMapper.selectById(activityId)).thenReturn(
                Activity.builder().id(activityId).placeId("ChIJ-guess").build());
        when(activityMapper.assignPlace(eq(activityId), eq(placeId.toString()), eq(placeId), any(), any()))
                .thenReturn(1);

        repository.assignPlace(activityId, placeId, BigDecimal.ONE, BigDecimal.TEN);

        assertThat(publishedReferences()).containsExactlyInAnyOrder("ChIJ-guess", placeId.toString());
    }

    @Test
    void countsForAPageComeFromOneGroupedQuery() {
        UUID withActivities = UUID.randomUUID();
        UUID without = UUID.randomUUID();
        when(activityMapper.countByTripIds(List.of(withActivities, without)))
                .thenReturn(List.of(new TripCount(withActivities, 4)));

        assertThat(repository.countByTripIds(List.of(withActivities, without, withActivities)))
                .containsEntry(withActivities, 4)
                .doesNotContainKey(without);
        verify(activityMapper, never()).countByTripId(any());
    }

    private Set<String> publishedReferences() {
        ArgumentCaptor<PlaceUsageChangedEvent> event = ArgumentCaptor.forClass(PlaceUsageChangedEvent.class);
        verify(events).publishEvent(event.capture());
        return event.getValue().placeReferences();
    }
}
