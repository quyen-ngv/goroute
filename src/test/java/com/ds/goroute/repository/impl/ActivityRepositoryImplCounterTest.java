package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.TripCount;
import com.ds.goroute.mapper.ActivityMapper;
import com.ds.goroute.mapper.TripMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every activity write keeps trips.activity_count (V216) in step, whoever the caller is. */
class ActivityRepositoryImplCounterTest {

    private final ActivityMapper activityMapper = mock(ActivityMapper.class);
    private final TripMapper tripMapper = mock(TripMapper.class);
    private final ActivityRepositoryImpl repository = new ActivityRepositoryImpl(activityMapper, tripMapper);

    @Test
    void insertRecountsItsTrip() {
        UUID tripId = UUID.randomUUID();

        repository.insert(Activity.builder().id(UUID.randomUUID()).tripId(tripId).name("Cafe").build());

        verify(activityMapper).insert(any());
        verify(tripMapper).refreshActivityCount(tripId);
    }

    @Test
    void deleteRecountsTheTripTheActivityBelongedTo() {
        UUID tripId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        when(activityMapper.selectTripIdById(activityId)).thenReturn(tripId);
        when(activityMapper.deleteById(activityId)).thenReturn(1);

        repository.deleteById(activityId);

        verify(tripMapper).refreshActivityCount(tripId);
    }

    @Test
    void deletingNothingTouchesNoTrip() {
        UUID activityId = UUID.randomUUID();
        when(activityMapper.deleteById(activityId)).thenReturn(0);

        repository.deleteById(activityId);

        verify(tripMapper, never()).refreshActivityCount(any());
    }

    @Test
    void deletingAllOfATripsActivitiesRecountsIt() {
        UUID tripId = UUID.randomUUID();

        repository.deleteByTripId(tripId);

        verify(tripMapper).refreshActivityCount(tripId);
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
}
