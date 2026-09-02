package com.ds.goroute.service.impl;

import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
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
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TripVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the anonymous public-trip endpoint against reading a SHARED (members-only) trip.
 *
 * <p>{@code getPublicTrip} used to check only for PRIVATE; a SHARED trip fell through to
 * fully open access because "not PRIVATE" is not the same claim as "safe to show anyone".
 * These tests exist so that mistake cannot silently come back -- a green build now means
 * a stranger genuinely cannot read a members-only trip through this door.
 */
class TripServiceImplPublicVisibilityTest {

    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);

    private TripServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripServiceImpl(
                mock(StarService.class),
                mock(TripRepository.class),
                // getPublicTrip answers for anonymous readers and never consults the guard.
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
                // Same-thread executor: the fire-and-forget view counter has to have run
                // by the time the assertions look at it.
                Runnable::run);
    }

    @Test
    void publicTripIsOpenToAnyone() {
        Trip trip = trip(TripVisibility.PUBLIC, UUID.randomUUID());

        assertTrue(service.canViewPublicTrip(trip, null));
        assertTrue(service.canViewPublicTrip(trip, UUID.randomUUID()));
    }

    @Test
    void privateTripIsNeverViewableHere() {
        UUID ownerId = UUID.randomUUID();
        Trip trip = trip(TripVisibility.PRIVATE, ownerId);

        assertFalse(service.canViewPublicTrip(trip, null));
        assertFalse(service.canViewPublicTrip(trip, ownerId));
    }

    @Test
    void sharedTripBlocksAnAnonymousViewer() {
        Trip trip = trip(TripVisibility.SHARED, UUID.randomUUID());

        assertFalse(service.canViewPublicTrip(trip, null));
    }

    @Test
    void sharedTripBlocksASignedInStrangerWhoIsNotAMember() {
        Trip trip = trip(TripVisibility.SHARED, UUID.randomUUID());
        UUID stranger = UUID.randomUUID();
        when(tripMemberRepository.findByTripIdAndUserId(trip.getId(), stranger))
                .thenReturn(Optional.empty());

        assertFalse(service.canViewPublicTrip(trip, stranger));
    }

    @Test
    void sharedTripBlocksAMemberWhoseInviteIsStillPending() {
        Trip trip = trip(TripVisibility.SHARED, UUID.randomUUID());
        UUID invitee = UUID.randomUUID();
        when(tripMemberRepository.findByTripIdAndUserId(trip.getId(), invitee))
                .thenReturn(Optional.of(TripMember.builder().status(MemberStatus.PENDING).build()));

        assertFalse(service.canViewPublicTrip(trip, invitee));
    }

    @Test
    void sharedTripAllowsTheOwnerWithoutAMembershipRow() {
        UUID ownerId = UUID.randomUUID();
        Trip trip = trip(TripVisibility.SHARED, ownerId);

        assertTrue(service.canViewPublicTrip(trip, ownerId));
    }

    @Test
    void sharedTripAllowsAnAcceptedMember() {
        Trip trip = trip(TripVisibility.SHARED, UUID.randomUUID());
        UUID member = UUID.randomUUID();
        when(tripMemberRepository.findByTripIdAndUserId(trip.getId(), member))
                .thenReturn(Optional.of(TripMember.builder().status(MemberStatus.ACCEPTED).build()));

        assertTrue(service.canViewPublicTrip(trip, member));
    }

    private Trip trip(TripVisibility visibility, UUID ownerId) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .visibility(visibility)
                .build();
    }
}
