package com.ds.goroute.service;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.type.MemberStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripAccessGuardTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    private final TripAccessGuard guard = new TripAccessGuard(tripRepository, tripMemberRepository);

    private final UUID tripId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private Trip trip() {
        Trip trip = Trip.builder().id(tripId).ownerId(ownerId).build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        return trip;
    }

    @Test
    void ownerIsAllowedWithoutAMembershipRow() {
        Trip trip = trip();
        assertThat(guard.requireAccess(tripId, ownerId)).isSameAs(trip);
    }

    @Test
    void acceptedMemberIsAllowed() {
        trip();
        when(tripMemberRepository.findByTripIdAndUserId(tripId, otherId)).thenReturn(
                Optional.of(TripMember.builder().status(MemberStatus.ACCEPTED).build()));

        assertThat(guard.requireAccess(tripId, otherId)).isNotNull();
    }

    @Test
    void pendingMemberIsRefused() {
        trip();
        when(tripMemberRepository.findByTripIdAndUserId(tripId, otherId)).thenReturn(
                Optional.of(TripMember.builder().status(MemberStatus.PENDING).build()));

        assertThatThrownBy(() -> guard.requireAccess(tripId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void strangerIsRefused() {
        trip();
        when(tripMemberRepository.findByTripIdAndUserId(tripId, otherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAccess(tripId, otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void missingTripIsReportedAsNotFoundRatherThanForbidden() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAccess(tripId, ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trip not found");
    }
}
