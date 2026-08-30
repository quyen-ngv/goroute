package com.ds.goroute.service.impl;

import com.ds.goroute.service.TripAccessGuard;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.notification.NotificationHelper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckinServiceAccessTest {

    private final CheckinRepository checkinRepository = mock(CheckinRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    private final CheckinServiceImpl service = new CheckinServiceImpl(
            checkinRepository,
            activityRepository,
            new TripAccessGuard(tripRepository, tripMemberRepository),
            tripRepository,
            tripMemberRepository,
            mock(UserRepository.class),
            mock(NotificationHelper.class));

    @Test
    void rejectsNonMemberBeforeReadingCheckins() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(tripRepository.findById(tripId))
                .thenReturn(Optional.of(Trip.builder().id(tripId).ownerId(ownerId).build()));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, requesterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCheckins(tripId, null, requesterId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");
        verify(checkinRepository, never()).findByTripId(tripId);
    }

    @Test
    void ownerReadsByTripIdRatherThanUserId() {
        UUID tripId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(tripRepository.findById(tripId))
                .thenReturn(Optional.of(Trip.builder().id(tripId).ownerId(ownerId).build()));
        when(checkinRepository.findByTripId(tripId)).thenReturn(List.of());

        assertThat(service.getCheckins(tripId, null, ownerId)).isEmpty();
        verify(checkinRepository).findByTripId(tripId);
    }
}
