package com.ds.goroute.event;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.PlaceUsageStatsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceUsageStatsListenerTest {

    private final PlaceUsageStatsService stats = mock(PlaceUsageStatsService.class);
    private final UserCheckinRepository checkins = mock(UserCheckinRepository.class);
    private final PlaceUsageStatsListener listener = new PlaceUsageStatsListener(stats, checkins);

    @Test
    void aChangedTripMarksItsPlaces() {
        listener.onPlaceUsageChanged(new PlaceUsageChangedEvent(Set.of("ChIJ-a")));

        verify(stats).markChanged(Set.of("ChIJ-a"));
    }

    @Test
    void aCheckinMarksItsPlaceWhetherAddedOrRemoved() {
        UUID checkinId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        when(checkins.findById(checkinId)).thenReturn(Optional.of(
                UserCheckin.builder().id(checkinId).placeId(placeId).build()));

        listener.onCheckinCreated(new CheckinCreatedEvent(checkinId, UUID.randomUUID()));
        listener.onCheckinRemoved(new CheckinRemovedEvent(checkinId, UUID.randomUUID()));

        verify(stats, org.mockito.Mockito.times(2)).markChanged(List.of(placeId.toString()));
    }

    @Test
    void aCheckinWithoutAPlaceMarksNothing() {
        UUID checkinId = UUID.randomUUID();
        when(checkins.findById(checkinId)).thenReturn(Optional.of(UserCheckin.builder().id(checkinId).build()));

        listener.onCheckinCreated(new CheckinCreatedEvent(checkinId, UUID.randomUUID()));

        verify(stats, never()).markChanged(any());
    }
}
