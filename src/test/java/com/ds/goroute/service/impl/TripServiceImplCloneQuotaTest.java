package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CloneTripRequest;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.repository.ReviewHelpfulVoteRepository;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.repository.TripHelpfulVoteRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripNoteRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.TripVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A clone is a trip, and has to be paid for like one.
 *
 * <p>Cloning wrote a trip without ever reserving a slot, so the quota was avoidable by
 * anyone who copied a public trip instead of starting their own -- the free path around
 * the only thing stars are currently spent on.
 */
class TripServiceImplCloneQuotaTest {

    private final StarService starService = mock(StarService.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);

    private TripServiceImpl service;

    private final UUID tripId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TripServiceImpl(
                starService,
                tripRepository,
                // Cloning has its own access rule -- the trip has to be public, or the cloner has
                // to already be in it -- so it never reaches the editor guard.
                mock(TripAccessGuard.class),
                tripMemberRepository,
                mock(UserRepository.class),
                mock(ActivityRepository.class),
                mock(ExpenseRepository.class),
                mock(ExpenseSplitRepository.class),
                mock(CheckinRepository.class),
                mock(TripNoteRepository.class),
                mock(NotificationHelper.class),
                mock(LocationImageService.class),
                mock(ExpenseService.class),
                mock(ExchangeRateService.class),
                mock(UserReviewRepository.class),
                mock(UserReviewProfileRepository.class),
                mock(ReviewHelpfulVoteRepository.class),
                mock(TripHelpfulVoteRepository.class),
                mock(PlaceScoreRepository.class),
                mock(MediaAssetRepository.class),
                mock(ImageStorageCleanupService.class),
                mock(TripDestinationRepository.class),
                mock(SocialNotificationService.class),
                Runnable::run);
    }

    private CloneTripRequest request() {
        return CloneTripRequest.builder()
                .name("Copy of a trip")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(2))
                .build();
    }

    private void publicTripExists() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(Trip.builder()
                .id(tripId).ownerId(UUID.randomUUID())
                .visibility(TripVisibility.PUBLIC).build()));
    }

    @Test
    void aCloneIsRefusedWhenThereIsNoSlotLeftToPayForIt() {
        publicTripExists();
        doThrow(new BusinessException(ErrorConstant.TRIP_CREATION_QUOTA_EXHAUSTED))
                .when(starService).reserveTripCreation(userId);

        assertThatThrownBy(() -> service.cloneTrip(tripId, request(), userId))
                .isInstanceOf(BusinessException.class);

        // Nothing was written: the refusal has to land before the copy, or the quota is
        // enforced only in the error message.
        verify(tripRepository, never()).insert(any());
    }

    /**
     * The reservation sits behind the access check, so a clone the user is not allowed to
     * make cannot spend a slot on its way to being refused.
     */
    @Test
    void aCloneNobodyIsAllowedToMakeCostsNothing() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(Trip.builder()
                .id(tripId).ownerId(UUID.randomUUID())
                .visibility(TripVisibility.PRIVATE).build()));
        when(tripMemberRepository.findByTripIdAndUserId(tripId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cloneTrip(tripId, request(), userId))
                .isInstanceOf(BusinessException.class);

        verify(starService, never()).reserveTripCreation(any());
        verify(tripRepository, never()).insert(any());
    }

    @Test
    void aTripThatDoesNotExistCostsNothing() {
        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cloneTrip(tripId, request(), userId))
                .isInstanceOf(BusinessException.class);

        verify(starService, never()).reserveTripCreation(any());
    }
}
