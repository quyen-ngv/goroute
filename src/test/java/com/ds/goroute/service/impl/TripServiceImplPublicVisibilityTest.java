package com.ds.goroute.service.impl;

import com.ds.goroute.entity.Trip;
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
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.TripVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pins who can read a trip through the anonymous public endpoint, which backs every shared link.
 *
 * <p>SHARED is the share dialog's "Link only": unlisted, but readable by anyone holding the link.
 * It was once restricted to members, which made every shared link answer "Trip not found" to
 * the people it was sent to. PRIVATE stays closed to everyone, the owner included.
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
                mock(com.ds.goroute.service.TripChatService.class),
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
                mock(TripRealtimePublisher.class),
                mock(com.ds.goroute.service.TrackService.class),
                mock(TripDestinationRepository.class),
                mock(SocialNotificationService.class),
                mock(UserCheckinService.class),
                // Same-thread executor: the fire-and-forget view counter has to have run
                // by the time the assertions look at it.
                Runnable::run,
                mock(com.ds.goroute.repository.ContentCommentRepository.class),
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @Test
    void publicTripIsOpenToAnyone() {
        Trip trip = trip(TripVisibility.PUBLIC, UUID.randomUUID());

        assertTrue(service.canViewPublicTrip(trip));
    }

    @Test
    void privateTripIsNeverViewableHere() {
        Trip trip = trip(TripVisibility.PRIVATE, UUID.randomUUID());

        assertFalse(service.canViewPublicTrip(trip));
    }

    @Test
    void sharedTripIsOpenToAnyoneHoldingTheLink() {
        Trip trip = trip(TripVisibility.SHARED, UUID.randomUUID());

        assertTrue(service.canViewPublicTrip(trip));
    }

    @Test
    void tripWithoutAVisibilityIsTreatedAsPrivate() {
        Trip trip = trip(null, UUID.randomUUID());

        assertFalse(service.canViewPublicTrip(trip));
    }

    private Trip trip(TripVisibility visibility, UUID ownerId) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .visibility(visibility)
                .build();
    }
}
